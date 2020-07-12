package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.exceptions.*;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cern.c2mon.daq.opcua.exceptions.CommunicationException.of;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * The implementation of wrapper for the OPC UA milo client. Except for the initialize and getParentObjectNodeId
 * methods, all thrown {@link OPCUAException}s can be either of type {@link CommunicationException} or {@link
 * LongLostConnectionException}. A CommunicationException ocurrs when the method has been attempted as often and with a
 * delay as configured without success. A LongLostConnectionException is thrown when the connection has already been
 * lost for the time needed to execute all configured attempts. In this case, no retries are executed.
 */
@Slf4j
@RequiredArgsConstructor
@Component(value = "miloEndpoint")
@Primary
@Scope(value = "prototype")
public class MiloEndpoint implements Endpoint, SessionActivityListener, UaSubscriptionManager.SubscriptionListener {

    private final SecurityModule securityModule;
    private final RetryDelegate retryDelegate;
    private final TagSubscriptionMapper mapper;
    private final MessageSender messageSender;
    private final BiMap<Integer, UaSubscription> subscriptionMap = HashBiMap.create();
    private final Collection<SessionActivityListener> sessionActivityListeners = new ArrayList<>();
    private OpcUaClient client;
    @Getter
    private String uri;
    private final AtomicLong disconnectedOn = new AtomicLong(0);

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
        final String hostRegex = "://[^/]*";

        final Matcher matcher = Pattern.compile(hostRegex).matcher(originalUri);
        if (matcher.find()) {
            return originalEndpoints.stream().map(e -> new EndpointDescription(
                    e.getEndpointUrl().replaceAll(hostRegex, matcher.group()),
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
            var endpoints = DiscoveryClient.getEndpoints(uri).join();
            endpoints = updateEndpointUrls(uri, endpoints);
            client = securityModule.createClientWithListeners(endpoints, sessionActivityListeners);
            client.getSubscriptionManager().addSubscriptionListener(this);
        } catch (CompletionException e) {
            log.error("Error on connection to uri {}: ", uri, e);
            throw of(CONNECT, e.getCause(), false);
        }
    }

    public void monitorEquipmentState(boolean shouldMonitor, SessionActivityListener listener) {
        listener = (listener == null) ? (SessionActivityListener) messageSender : listener;
        if (shouldMonitor && !sessionActivityListeners.contains(listener)) {
            sessionActivityListeners.add(listener);
            client.addSessionActivityListener(listener);
            synchronized (this) {
                if (disconnectedOn.get() <= 1L) {
                    listener.onSessionActive(null);
                }
            }
        } else {
            sessionActivityListeners.remove(listener);
            client.removeSessionActivityListener(listener);
        }
    }

    /**
     * Disconnect from the server with a certain number of retries in case of connection error.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public synchronized void disconnect() throws OPCUAException {
        log.info("Disconnecting endpoint at {}", uri);
        if (client != null) {
            client.getSubscriptionManager().clearSubscriptions();
            client.getSubscriptionManager().removeSubscriptionListener(this);
            sessionActivityListeners.forEach(l -> client.removeSessionActivityListener(l));
            retryDelegate.completeOrThrow(DISCONNECT, this::getDisconnectPeriod, client::disconnect);
            client = null;
        } else {
            log.info("Client not connected, skipping disconnection attempt.");
        }
        sessionActivityListeners.clear();
        subscriptionMap.clear();
        disconnectedOn.set(-1);
    }

    /**
     * Delete an existing subscription with a certain number of retries in case of connection error.
     * @param timeDeadband The timeDeadband of the subscription to delete along with all contained MonitoredItems.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public void deleteSubscription(int timeDeadband) throws OPCUAException {
        final UaSubscription subscription = subscriptionMap.remove(timeDeadband);
        if (subscription != null) {
            retryDelegate.completeOrThrow(DELETE_SUBSCRIPTION, this::getDisconnectPeriod,
                    () -> client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
        }
    }

    /**
     * Add a list of item definitions as monitored items to a subscription with a certain number of retries in case of
     * connection error and notify the endpointListener of future value updates
     * @param publishingInterval The publishing interval to request for the item definitions
     * @param definitions        the {@link ItemDefinition}s for which to create monitored items
     * @return valid client handles
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    public Map<UInteger, SourceDataTagQuality> subscribeToGroup(int publishingInterval, Collection<ItemDefinition> definitions) throws OPCUAException {
        log.info("Subscribing definition {} with publishing interval {}.", definitions, publishingInterval);
        return subscribeWithCallback(publishingInterval, definitions, (item, i) -> item.setValueConsumer(value -> {
            log.info("Creating callback for definition {}.", definitions);
            SourceDataTagQuality tagQuality = MiloMapper.getDataTagQuality((value.getStatusCode() == null) ? StatusCode.BAD : value.getStatusCode());
            final Object updateValue = MiloMapper.toObject(value.getValue());
            ValueUpdate valueUpdate = (value.getSourceTime() == null) ?
                    new ValueUpdate(updateValue) :
                    new ValueUpdate(updateValue, value.getSourceTime().getJavaTime());
            messageSender.onValueUpdate(item.getClientHandle(), tagQuality, valueUpdate);
        }));
    }

    public Map<UInteger, SourceDataTagQuality> subscribeWithCallback(int publishingInterval,
                                                                     Collection<ItemDefinition> definitions,
                                                                     BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCUAException {
        var subscription = getOrCreateSubscription(publishingInterval);
        var requests = definitions.stream().map(this::toMonitoredItemCreateRequest).collect(toList());
        return retryDelegate
                .completeOrThrow(CREATE_MONITORED_ITEM, this::getDisconnectPeriod,
                        () -> subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback))
                .stream()
                .collect(toMap(UaMonitoredItem::getClientHandle, item -> MiloMapper.getDataTagQuality(item.getStatusCode())));
    }

    /**
     * Called when a subscription could not be automatically transferred in between sessions. In this case, the
     * subscription is recreated from scratch. If the controller received the command to stop, the subscription is not
     * recreated and the method exists silently.
     * @param subscription the session which must be recreated.
     * @throws OPCUAException of type {@link CommunicationException} if the subscription could not be recreated due to
     *                        communication difficulty, and of type {@link ConfigurationException} if the subscription
     *                        can not be mapped to current DataTags, and therefore cannot be recreated.
     */
    public synchronized void recreateSubscription(UaSubscription subscription) throws OPCUAException {
        if (disconnectedOn.get() < 0L || Thread.currentThread().isInterrupted()) {
            log.info("Client was stopped, cease subscription recreation attempts.");
            return;
        }
        log.info("Attempt to recreate the subscription.");
        final Integer publishInterval = subscriptionMap.inverse().get(subscription);
        final var group = publishInterval == null ? null : mapper.getGroup(publishInterval);
        if (group == null || group.size() == 0) {
            throw new ConfigurationException(ExceptionContext.EMPTY_SUBSCRIPTION);
        }
        try {
            deleteSubscription(publishInterval);
        } catch (OPCUAException ignored) {
            log.error("Could not delete subscription. Proceed with recreation.");
        }
        try {
            if (subscribeToGroup(publishInterval, group.getTagIds().values())
                    .entrySet()
                    .stream()
                    .noneMatch(e -> e.getValue().isValid())) {
                throw new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION);
            }
        } finally {
            subscriptionMap.putIfAbsent(publishInterval, subscription);
        }
    }

    /**
     * Delete an item from an OPC UA subscription with a certain number of retries in case of connection. If the
     * subscription contains only the one item, the whole subscription is deleted. error.
     * @param clientHandle    the identifier of the monitored item to remove.
     * @param publishInterval the publishing interval subscription to remove the monitored item from.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public void deleteItemFromSubscription(UInteger clientHandle, int publishInterval) throws OPCUAException {
        final UaSubscription subscription = subscriptionMap.get(publishInterval);
        if (subscription != null && subscription.getMonitoredItems().size() <= 1) {
            deleteSubscription(publishInterval);
        } else if (subscription != null) {
            List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems().stream()
                    .filter(i -> clientHandle.equals(i.getClientHandle()))
                    .collect(toList());
            retryDelegate.completeOrThrow(DELETE_MONITORED_ITEM, this::getDisconnectPeriod,
                    () -> subscription.deleteMonitoredItems(itemsToRemove));
        }
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
        return Map.entry(
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
     * Call the method node with ID methodId contained in the object with ID objectId with a certain number of retries
     * in case of connection error.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param arg      the input arguments to pass to the methodId call.
     * @return the StatusCode of the methodId response as key and the output arguments of the called method (if
     * applicable, else null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    public Map.Entry<Boolean, Object[]> callMethod(NodeId objectId, NodeId methodId, Object arg) throws OPCUAException {
        final var variants = arg == null ? null : new Variant[]{new Variant(arg)};
        final CallMethodResult result = retryDelegate.completeOrThrow(METHOD, this::getDisconnectPeriod,
                () -> client.call(new CallMethodRequest(objectId, methodId, variants)));
        final StatusCode statusCode = result.getStatusCode();
        log.info("Calling method {} on object {} returned status code {}.", methodId, objectId, statusCode);
        return Map.entry(statusCode.isGood(), MiloMapper.toObject(result.getOutputArguments()));
    }

    /**
     * Fetches the node's first parent object node, if such a node exists with a certain number of retries in case of
     * connection error.
     * @param nodeId the node whose parent object to fetch
     * @return the parent node's NodeId, if it exists. Else a {@link ConfigurationException} is thrown.
     * @throws OPCUAException of type {@link ConfigurationException} in case the nodeId is orphaned, or of types {@link
     *                        CommunicationException} or {@link LongLostConnectionException} as detailed in the
     *                        implementation class's JavaDoc.
     */
    public NodeId getParentObjectNodeId(NodeId nodeId) throws OPCUAException {
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
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    public ServerRedundancyTypeNode getServerRedundancyNode() throws OPCUAException {
        return retryDelegate.completeOrThrow(SERVER_NODE, this::getDisconnectPeriod,
                () -> client.getAddressSpace().getObjectNode(Identifiers.Server_ServerRedundancy, ServerRedundancyTypeNode.class));
    }

    /**
     * Check whether a subscription is subscribed as part of the current session.
     * @param subscription the subscription to check for currentness.
     * @return true if the subscription is a valid in the current session.
     */
    public boolean isCurrent(UaSubscription subscription) {
        return client.getSubscriptionManager().getSubscriptions().contains(subscription);
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
     * Attempt recreating the subscription periodically unless the subscription's monitored items cannot be mapped to
     * DataTags due to a configuration mismatch. Recreation attempts are discontinued when the thread is interrupted or
     * recreation finishes successfully.
     * @param subscription the subscription to recreate on the client.
     */
    private void recreate(UaSubscription subscription) {
        try {
            retryDelegate.triggerRecreateSubscription(this, subscription);
            log.info("Subscription successfully recreated!");
        } catch (CommunicationException e) {
            // only after Integer.MAX_VALUE retries have failed, should not happen
            recreate(subscription);
        } catch (BackOffInterruptedException ignored) {
            log.error("Recreation process interrupted and discontinued.");
        } catch (Exception e) {
            log.error("Subscription recreation discontinued: ", e);
        }
    }

    /**
     * Create and return a new subscription with a certain number of retries in case of connection error.
     * @param timeDeadband The subscription's publishing interval in milliseconds. If 0, the Server will use the fastest
     *                     supported interval.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    private UaSubscription getOrCreateSubscription(int timeDeadband) throws OPCUAException {
        var subscription = subscriptionMap.get(timeDeadband);
        if (subscription != null && !isCurrent(subscription)) {
            subscription = null;
        }
        if (subscription == null) {
            subscription = retryDelegate.completeOrThrow(CREATE_SUBSCRIPTION, this::getDisconnectPeriod, () -> client.getSubscriptionManager().createSubscription(timeDeadband));
            subscriptionMap.put(timeDeadband, subscription);
        }
        return subscription;
    }

    private MonitoredItemCreateRequest toMonitoredItemCreateRequest(ItemDefinition definition) {
        // If a static time deadband is configured, only one value per publishing cycle will be kept by the DAQ core.
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

    private synchronized long getDisconnectPeriod() {
        final long l = disconnectedOn.get();
        return l > 0L ? System.currentTimeMillis() - l : l;
    }
}
