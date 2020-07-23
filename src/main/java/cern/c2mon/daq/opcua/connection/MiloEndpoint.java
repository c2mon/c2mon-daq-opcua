package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.exceptions.*;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.*;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.CommunicationException.of;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * An implementation of {@link Endpoint} relying on Eclipse Milo. Handles all interaction with a single server including
 * the the consolidation of security settings. Maintains a mapping in between the per-server time Deadbands and
 * associated subscription IDs. Informs the {@link IMessageSender} of the state of connection and of new values. A
 * {@link CommunicationException} occurs when the method has been attempted as often and with a delay as configured
 * without success. A {@link LongLostConnectionException} is thrown when the connection has already been lost for the
 * time needed to execute all configured attempts. In this case, no retries are executed. {@link
 * ConfigurationException}s are never retried, and represent failures that require a configuration change to fix.
 */
@Slf4j
@RequiredArgsConstructor
@Component(value = "miloEndpoint")
@Primary
@Scope(value = "prototype")
public class MiloEndpoint implements Endpoint, SessionActivityListener, UaSubscriptionManager.SubscriptionListener {
    private static final String HOST_REGEX = "://[^/]*";
    private final SecurityModule securityModule;
    private final RetryDelegate retryDelegate;
    private final TagSubscriptionReader mapper;
    private final IMessageSender messageSender;
    private final BiMap<Integer, UaSubscription> subscriptionMap = HashBiMap.create();
    private final Collection<SessionActivityListener> sessionActivityListeners = new ArrayList<>();
    private final AtomicLong disconnectedOn = new AtomicLong(0);
    private final RetryTemplate exponentialDelayTemplate;
    private OpcUaClient client;
    @Getter
    private String uri;
    @Setter
    private MonitoringMode mode = MonitoringMode.Reporting;

    /**
     * There is a common misconfiguration in OPC UA servers to return a local hostname in the endpointUrl that can not
     * be resolved by the client. Replace the hostname of each endpoint's URL by the one used to originally reach the
     * server to work around the issue.
     * @param originalUri       the (resolvable) uri used to originally reach the server.
     * @param originalEndpoints a list of originalEndpoints returned by the DiscoveryClient potentially containing
     *                          incorrect endpointUrls
     * @return a list of endpoints with the original url as as endpointUrl, but other identical to the
     * originalEndpoints.
     */
    private static List<EndpointDescription> updateEndpointUrls(String originalUri, List<EndpointDescription> originalEndpoints) {
        // Some hostnames contain characters not allowed in a URI, such as underscores in Windows machine hostnames.
        // Therefore, parsing is done using a regular expression rather than relying on java URI class methods.

        final Matcher matcher = Pattern.compile(HOST_REGEX).matcher(originalUri);
        if (matcher.find()) {
            return originalEndpoints.stream().map(e -> new EndpointDescription(
                    e.getEndpointUrl().replaceAll(HOST_REGEX, matcher.group()),
                    e.getServer(),
                    e.getServerCertificate(),
                    e.getSecurityMode(),
                    e.getSecurityPolicyUri(),
                    e.getUserIdentityTokens(),
                    e.getTransportProfileUri(),
                    e.getSecurityLevel()))
                    .collect(toList());
        }
        return originalEndpoints;
    }

    /**
     * Connects to a server through the Milo OPC UA SDK. Discover available endpoints and select the most secure one in
     * line with configuration options. In case of a communication error this method is retried a given amount of times
     * with a given delay as specified in the application configuration.
     * @param uri the server address to connect to.
     * @throws OPCUAException of type {@link CommunicationException} if it is not possible to connect to the OPC UA
     *                        server within the configured number of attempts, and of type {@link
     *                        ConfigurationException} if it is not possible to connect to any of the the OPC UA server's
     *                        endpoints with the given authentication configuration settings.
     */
    @Override
    @Retryable(value = {CommunicationException.class},
            maxAttemptsExpression = "${app.maxRetryAttempts}",
            backoff = @Backoff(delayExpression = "${app.retryDelay}"))
    public void initialize(String uri) throws OPCUAException {
        this.uri = uri;
        sessionActivityListeners.add(this);
        disconnectedOn.set(0L);
        try {
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(uri).join();
            endpoints = updateEndpointUrls(uri, endpoints);
            client = securityModule.createClientWithListeners(endpoints, sessionActivityListeners);
            final OpcUaSubscriptionManager subscriptionManager = client.getSubscriptionManager();
            subscriptionManager.addSubscriptionListener(this);
            subscriptionManager.resumeDelivery();
        } catch (CompletionException e) {
            log.error("Error on connection to uri {}: ", uri, e);
            throw of(CONNECT, e.getCause(), false);
        }
    }

    /**
     * Adds or removes {@link SessionActivityListener}s from the client.
     * @param add      whether to add or remove a listener. If the listener is already in the intended state, no action
     *                 is taken.
     * @param listener the listener to add or remove.  If null, {@link IMessageSender} is used.
     */
    @Override
    public void manageSessionActivityListener(boolean add, SessionActivityListener listener) {
        if (null == listener) {
            listener = (SessionActivityListener) messageSender;
        }
        if (add && !sessionActivityListeners.contains(listener)) {
            sessionActivityListeners.add(listener);
            client.addSessionActivityListener(listener);
            synchronized (this) {
                if (disconnectedOn.get() <= 1L) {
                    listener.onSessionActive(null);
                }
            }
        } else if (!add) {
            sessionActivityListeners.remove(listener);
            client.removeSessionActivityListener(listener);
        } else {
            log.info("Nothing to do.");
        }
    }

    /**
     * Disconnect from the server with a certain number of retries in case of connection error.
     */
    @Override
    public void disconnect() {
        log.info("Disconnecting endpoint at {}", uri);
        try {
            if (client != null) {
                client.getSubscriptionManager().clearSubscriptions();
                client.getSubscriptionManager().removeSubscriptionListener(this);
                sessionActivityListeners.forEach(l -> client.removeSessionActivityListener(l));
                retryDelegate.completeOrThrow(DISCONNECT, this::getDisconnectPeriod, client::disconnect);
            } else {
                log.info("Client not connected, skipping disconnection attempt.");
            }
        } catch (OPCUAException ex) {
            log.error("Error disconnecting from endpoint with uri {}: ", uri, ex);
        } finally {
            sessionActivityListeners.clear();
            subscriptionMap.clear();
            disconnectedOn.set(-1);
        }
        log.info("Completed disconnecting endpoint {}", uri);
    }

    /**
     * Add a list of item definitions as monitored items to a subscription with a certain number of retries in case of
     * connection fault and notify the {@link IMessageSender} of all future value updates
     * @param group       The {@link SubscriptionGroup} to add the {@link ItemDefinition}s to
     * @param definitions the {@link ItemDefinition}s for which to create monitored items
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws ConfigurationException if the server returned an error code indicating a misconfiguration.
     */
    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) throws ConfigurationException {
        try {
            log.info("Subscribing definitions with publishing interval {}.", group.getPublishInterval());
            return subscribeWithCallback(group.getPublishInterval(), definitions, this::defaultSubscriptionCallback);
        } catch (ConfigurationException e) {
            throw e;
        } catch (OPCUAException e) {
            final String ids = definitions.stream()
                    .map(d -> mapper.getTagId(d.getClientHandle()).toString())
                    .collect(Collectors.joining(", "));
            log.error("Tags with IDs {} could not be subscribed on endpoint with uri {}. ", ids, getUri(), e);
            return definitions.stream()
                    .map(definition -> new AbstractMap.SimpleEntry<>(definition.getClientHandle(), new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }



    /**
     * Recreate all subscriptions configured in {@link TagSubscriptionReader}. This method is usually called by the
     * {@link cern.c2mon.daq.opcua.control.Controller} after a failover.
     * @throws CommunicationException if no subscription could be recreated.
     */
    @Override
    public void recreateAllSubscriptions() throws CommunicationException {
        boolean anySuccess = true;
        for (SubscriptionGroup group : mapper.getGroups()) {
            try {
                anySuccess = anySuccess && resubscribeGroupsAndReportSuccess(group);
            } catch (ConfigurationException e) {
                log.info("Could not resubscribe group with time Deadband {}.", group.getPublishInterval(), e);
            }
        }
        if (!anySuccess) {
            log.error("Could not recreate any subscriptions. Connect to next server... ");
            throw new CommunicationException(ExceptionContext.NO_REDUNDANT_SERVER);
        }
        log.info("Recreated subscriptions on server {}.", uri);
    }

    /**
     * Subscribes to a {@link NodeId} with the passed callback. Usually called by the {@link
     * cern.c2mon.daq.opcua.control.Controller} to monitor the server health.
     * @param publishingInterval   the
     * @param definitions          the {@link ItemDefinition}s containing the {@link NodeId}s to subscribe to
     * @param itemCreationCallback the callback to apply upon creation of the items in the subscription
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws OPCUAException if the definitions could not be subscribed.
     */
    public Map<UInteger, SourceDataTagQuality> subscribeWithCallback(int publishingInterval,
                                                                     Collection<ItemDefinition> definitions,
                                                                     Consumer<UaMonitoredItem> itemCreationCallback) throws OPCUAException {
        UaSubscription subscription = getOrCreateSubscription(publishingInterval);
        List<MonitoredItemCreateRequest> requests = definitions.stream().map(this::toMonitoredItemCreateRequest).collect(toList());
        return retryDelegate
                .completeOrThrow(CREATE_MONITORED_ITEM, this::getDisconnectPeriod,
                        () -> subscription.createMonitoredItems(TimestampsToReturn.Both, requests, (item, i) -> itemCreationCallback.accept(item)))
                .stream()
                .collect(toMap(UaMonitoredItem::getClientHandle, item -> MiloMapper.getDataTagQuality(item.getStatusCode())));
    }

    /**
     * Delete an item from an OPC UA subscription with a certain number of retries in case of connection. If the
     * subscription contains only the one item, the whole subscription is deleted. error.
     * @param clientHandle    the identifier of the monitored item to remove.
     * @param publishInterval the publishing interval subscription to remove the monitored item from.
     */
    @Override
    public boolean deleteItemFromSubscription(UInteger clientHandle, int publishInterval) {
        try {
            final UaSubscription subscription = subscriptionMap.get(publishInterval);
            if (subscription != null && subscription.getMonitoredItems().size() <= 1) {
                deleteSubscription(publishInterval);
            } else if (subscription != null) {
                List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems().stream()
                        .filter(i -> clientHandle.equals(i.getClientHandle()))
                        .collect(toList());
                retryDelegate.completeOrThrow(DELETE_MONITORED_ITEM, this::getDisconnectPeriod,
                        () -> subscription.deleteMonitoredItems(itemsToRemove));
            } else {
                log.info("Item cannot be mapped to a subscription. Skipping deletion.");
            }
        } catch (OPCUAException ex) {
            log.error("Tag with ID {} could not be completed successfully on endpoint {}.", mapper.getTagId(clientHandle), getUri(), ex);
            return false;
        }
        return true;
    }

    /**
     * Read the current value from a node on the currently connected OPC UA server with a certain number of retries in
     * case connection of error.
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link DataValue} containing the value read from the node.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        final DataValue value = retryDelegate.completeOrThrow(READ, this::getDisconnectPeriod,
                () -> client.readValue(0, TimestampsToReturn.Both, nodeId));
        if (value.getSourceTime() == null || value.getStatusCode() == null) {
            throw new ConfigurationException(READ);
        }
        return new AbstractMap.SimpleEntry<>(
                new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime()),
                MiloMapper.getDataTagQuality(value.getStatusCode()));
    }

    /**
     * Write a value to a node on the currently connected OPC UA server with a certain number of retries in case of
     * connection error.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write action finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        // Many OPC UA Servers are unable to deal with StatusCode or DateTime, hence set to null
        DataValue dataValue = new DataValue(new Variant(value), null, null);
        final StatusCode statusCode = retryDelegate.completeOrThrow(WRITE, this::getDisconnectPeriod, () -> client.writeValue(nodeId, dataValue));
        log.info("Writing value {} to node {} yielded status code {}.", value, nodeId, statusCode);
        return statusCode.isGood();
    }

    /**
     * Call the method node associated with the definition with a certain number of retries in case of connection
     * error.. It no method node is specified within the definition, it is assumed that the primary node is the method
     * node, and the first object node ID encountered during browse is used as object node.
     * @param definition the definition containing the nodeId of Method which shall be called
     * @param arg        the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return (definition.getMethodNodeId() == null)
                ? callMethod(getParentObjectNodeId(definition.getNodeId()), definition.getNodeId(), arg)
                : callMethod(definition.getNodeId(), definition.getMethodNodeId(), arg);
    }

    /**
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() throws OPCUAException {
        return retryDelegate.completeOrThrow(SERVER_NODE, this::getDisconnectPeriod,
                () -> client.getAddressSpace().getObjectNode(Identifiers.Server_ServerRedundancy, ServerRedundancyTypeNode.class));
    }

    /**
     * Called when the client is connected to the server and the session activates.
     * @param session the current session
     */
    @Override
    public void onSessionActive(UaSession session) {
        disconnectedOn.getAndUpdate(l -> Math.min(l, 0L));
    }

    /**
     * Called when the client is disconnected from the server and the session deactivates.
     * @param session the current session
     */
    @Override
    public void onSessionInactive(UaSession session) {
        disconnectedOn.getAndUpdate(l -> l < 0 ? l : System.currentTimeMillis());
    }

    /**
     * When neither PublishRequests nor Service calls have been delivered to the server for a specific subscription for
     * a certain period of time, the subscription closes automatically and OnStatusChange is called with the status code
     * Bad_Timeout. The subscription's monitored items are then deleted automatically by the server. The other status
     * changes (CLOSED, CREATING, NORMAL, LATE, KEEPALIVE) don't need to be handled specifically. This event should not
     * occur regularly.
     * @param subscription the subscription which times out
     * @param status       the new status of the subscription
     */
    @Override
    public void onStatusChanged(UaSubscription subscription, StatusCode status) {
        log.info("onStatusChanged event for {} : StatusCode {}", subscription.toString(), status);
        if (status.isBad() && status.getValue() == StatusCodes.Bad_Timeout) {
            recreate(subscription);
        }
    }

    /**
     * If a Subscription is transferred to another Session, the queued Notification Messages for this subscription are
     * moved from the old to the new subscription. If this process fails, the subscription must be recreated from
     * scratch for the new session.
     * @param subscription the old subscription which could not be recreated regardless of the status code.
     * @param statusCode   the code delivered by the server giving the reason of the subscription failure. It can be any
     *                     of the codes "Bad_NotImplemented", "Bad_NotSupported", "Bad_OutOfService" or
     *                     "Bad_ServiceUnsupported"
     */
    @Override
    public void onSubscriptionTransferFailed(UaSubscription subscription, StatusCode statusCode) {
        log.info("onSubscriptionTransferFailed event for {} : StatusCode {}", subscription.toString(), statusCode);
        recreate(subscription);
    }

    /**
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    private NodeId getParentObjectNodeId(NodeId nodeId) throws OPCUAException {
        final BrowseDescription bd = new BrowseDescription(
                nodeId,
                BrowseDirection.Inverse,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue()),
                uint(BrowseResultMask.All.getValue()));
        final BrowseResult result = retryDelegate.completeOrThrow(BROWSE, this::getDisconnectPeriod, () -> client.browse(bd));
        if (result.getReferences() != null && result.getReferences().length > 0) {
            final Optional<NodeId> objectNode = result.getReferences()[0].getNodeId().local(client.getNamespaceTable());
            if (result.getStatusCode().isGood() && objectNode.isPresent()) {
                return objectNode.get();
            }
        }
        throw new ConfigurationException(OBJINVALID);
    }

    /**
     * Delete an existing subscription with a certain number of retries in case of connection error.
     * @param timeDeadband The timeDeadband of the subscription to delete along with all contained MonitoredItems.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    private void deleteSubscription(int timeDeadband) throws OPCUAException {
        final UaSubscription subscription = subscriptionMap.remove(timeDeadband);
        if (subscription != null) {
            retryDelegate.completeOrThrow(DELETE_SUBSCRIPTION, this::getDisconnectPeriod,
                    () -> client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
        }
    }

    private boolean resubscribeGroupsAndReportSuccess(SubscriptionGroup group) throws ConfigurationException {
        final Map<UInteger, SourceDataTagQuality> handleQualityMap = subscribe(group, group.getTagIds().values());
        return handleQualityMap != null && handleQualityMap.values().stream().anyMatch(SourceDataTagQuality::isValid);
    }

    private Map.Entry<Boolean, Object[]> callMethod(NodeId objectId, NodeId methodId, Object arg) throws OPCUAException {
        final Variant[] variants = arg == null ? null : new Variant[]{new Variant(arg)};
        final CallMethodResult result = retryDelegate.completeOrThrow(METHOD, this::getDisconnectPeriod,
                () -> client.call(new CallMethodRequest(objectId, methodId, variants)));
        final StatusCode statusCode = result.getStatusCode();
        log.info("Calling method {} on object {} returned status code {}.", methodId, objectId, statusCode);
        return new AbstractMap.SimpleEntry<>(statusCode.isGood(), MiloMapper.toObject(result.getOutputArguments()));
    }

    /**
     * Attempt to recreate the subscription periodically unless the subscription's monitored items cannot be mapped to
     * DataTags due to a configuration mismatch. Recreation attempts are discontinued when the thread is interrupted or
     * recreation finishes successfully.
     * @param subscription the subscription to recreate on the client.
     */
    private void recreate(UaSubscription subscription) {
        log.info("Attempt to recreate the subscription.");
        final Integer publishInterval = subscriptionMap.inverse().getOrDefault(subscription, -1);
        final SubscriptionGroup group = mapper.getGroup(publishInterval);
        if (group != null && group.size() != 0) {
            try {
                deleteSubscription(publishInterval);
            } catch (OPCUAException e) {
                log.error("Could not delete subscription. Proceed with recreation.", e);
            }
            try {
                exponentialDelayTemplate.execute(recreateWithRetry(group));
            } catch (OPCUAException e) {
                log.error("Retry logic is not correctly configured! Retries ceased.", e);
            }
        } else {
            log.info("The subscription cannot be recreated, since it cannot be associated with any DataTags.");
        }
    }

    private RetryCallback<Object, OPCUAException> recreateWithRetry(SubscriptionGroup group) {
        return retryContext -> {
            log.info("Retry number {} to create subscription with publish interval {}.", retryContext.getRetryCount(), group.getPublishInterval());
            if (disconnectedOn.get() < 0L || Thread.currentThread().isInterrupted()) {
                log.info("Client was stopped, cease subscription recreation attempts.");
            } else if (retryContext.getLastThrowable() != null && !(retryContext.getLastThrowable() instanceof CommunicationException)) {
                log.error("Subscription recreation aborted due to an unexpected error: ", retryContext.getLastThrowable());
            } else if (!resubscribeGroupsAndReportSuccess(group)) {
                throw new CommunicationException(CREATE_SUBSCRIPTION);
            }
            return null;
        };
    }

    private UaSubscription getOrCreateSubscription(int timeDeadband) throws OPCUAException {
        UaSubscription subscription = subscriptionMap.get(timeDeadband);
        if (subscription == null || !client.getSubscriptionManager().getSubscriptions().contains(subscription)) {
            subscription = retryDelegate.completeOrThrow(CREATE_SUBSCRIPTION, this::getDisconnectPeriod,
                    () -> client.getSubscriptionManager().createSubscription(timeDeadband));
            subscriptionMap.put(timeDeadband, subscription);
        }
        return subscription;
    }

    private void defaultSubscriptionCallback(UaMonitoredItem item) {
        item.setValueConsumer(value -> {
            final Long tagId = mapper.getTagId(item.getClientHandle());
            if (tagId == null) {
                log.info("Receives a value update that could not be associated with a DataTag.");
            } else {
                SourceDataTagQuality tagQuality = MiloMapper.getDataTagQuality((value.getStatusCode() == null) ? StatusCode.BAD : value.getStatusCode());
                final Object updateValue = MiloMapper.toObject(value.getValue());
                ValueUpdate valueUpdate = (value.getSourceTime() == null) ?
                        new ValueUpdate(updateValue) :
                        new ValueUpdate(updateValue, value.getSourceTime().getJavaTime());
                messageSender.onValueUpdate(tagId, tagQuality, valueUpdate);
            }
        });
    }

    private MonitoredItemCreateRequest toMonitoredItemCreateRequest(ItemDefinition definition) {
        // If a static time Deadband is configured, only one value per publishing cycle will be kept by the DAQ core.
        // Therefore queueSize can be 0 (only the newest value is held in between publishing cycles)
        UInteger queueSize = uint(0);

        // If the samplingInterval is set to 0, the source will provide updates at the fastest possible rate.
        double samplingInterval = 0;

        DataChangeFilter filter = new DataChangeFilter(DataChangeTrigger.StatusValue,
                uint(definition.getValueDeadbandType()),
                (double) definition.getValueDeadband());
        MonitoringParameters mp = new MonitoringParameters(definition.getClientHandle(),
                samplingInterval,
                ExtensionObject.encode(client.getSerializationContext(), filter),
                queueSize,
                true);
        ReadValueId id = new ReadValueId(definition.getNodeId(),
                AttributeId.Value.uid(),
                null,
                QualifiedName.NULL_VALUE);
        return new MonitoredItemCreateRequest(id, mode, mp);
    }

    private long getDisconnectPeriod() {
        final long l = disconnectedOn.get();
        return l > 0L ? System.currentTimeMillis() - l : l;
    }
}
