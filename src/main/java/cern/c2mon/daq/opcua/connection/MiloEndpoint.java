package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
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
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.OK;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * An implementation of {@link Endpoint} relying on Eclipse Milo. Handles all interaction with a single server including
 * the the consolidation of security settings. Maintains a mapping in between the per-server time Deadbands and
 * associated subscription IDs. Informs the {@link MessageSender} of the state of connection and of new values. A {@link
 * CommunicationException} occurs when the method has been attempted as often and with a delay as configured without
 * success. A {@link LongLostConnectionException} is thrown when the connection has already been lost for the time
 * needed to execute all configured attempts. In this case, no retries are executed. {@link ConfigurationException}s are
 * never retried, and represent failures that require a configuration change to fix. {@link
 * EndpointDisconnectedException}s are never retried, as they indicate that a call is an asynchronously remaining
 * process of an endpoint that has been disconnected.
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Scope("prototype")
public class MiloEndpoint implements Endpoint, SessionActivityListener, UaSubscriptionManager.SubscriptionListener {

    /**
     * Shows the instance of disconnection. 0 denotes that the endpoint is currently connected to the server, -1 that
     * the endpoint has been stopped.
     */
    private final AtomicLong disconnectedOn = new AtomicLong(0);
    private final SecurityModule securityModule;
    private final RetryDelegate retryDelegate;
    private final TagSubscriptionReader mapper;
    private final MessageSender messageSender;
    private final AppConfigProperties config;
    private final BiMap<Integer, UaSubscription> subscriptionMap = HashBiMap.create();
    private final Collection<SessionActivityListener> sessionActivityListeners = new ArrayList<>();
    private OpcUaClient client;
    private boolean updateEquipmentStateOnSessionChanges;

    @Getter
    private String uri;

    @Setter
    private MonitoringMode mode = MonitoringMode.Reporting;

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
    public void initialize(String uri) throws OPCUAException {
        this.uri = uri;
        disconnectedOn.set(0L);
        final Collection<EndpointDescription> endpoints = retryDelegate.completeOrRetry(CONNECT,
                this::getDisconnectPeriod,
                () -> DiscoveryClient.getEndpoints(uri));
        client = securityModule.createClient(uri, endpoints);
        client.addSessionActivityListener(this);
        sessionActivityListeners.add(this);
        final OpcUaSubscriptionManager subscriptionManager = client.getSubscriptionManager();
        subscriptionManager.addSubscriptionListener(this);
        subscriptionManager.resumeDelivery();
    }

    /**
     * Adds or removes {@link SessionActivityListener}s from the client.
     * @param add      whether to add or remove a listener. If the listener is already in the intended state, no action
     *                 is taken.
     * @param listener the listener to add or remove.
     */
    @Override
    public void manageSessionActivityListener(boolean add, SessionActivityListener listener) {
        if (add && !sessionActivityListeners.contains(listener)) {
            sessionActivityListeners.add(listener);
            client.addSessionActivityListener(listener);
            if (disconnectedOn.get() <= 1L) {
                listener.onSessionActive(null);
            }
        } else if (!add && sessionActivityListeners.contains(listener)) {
            sessionActivityListeners.remove(listener);
            client.removeSessionActivityListener(listener);
        } else {
            log.info("Nothing to do.");
        }
    }

    @Override
    public void setUpdateEquipmentStateOnSessionChanges(boolean active) {
        updateEquipmentStateOnSessionChanges = active;
        if (active && disconnectedOn.get() <= 1L) {
            messageSender.onEquipmentStateUpdate(OK);
        }
    }

    /**
     * Disconnect from the server with the number of retries configured in {@link cern.c2mon.daq.opcua.config.AppConfigProperties}
     * in case of connection error.
     */
    @Override
    public void disconnect() {
        log.info("Disconnecting endpoint at {}", uri);
        if (client != null) {
            try {
                client.getSubscriptionManager().clearSubscriptions();
                client.getSubscriptionManager().removeSubscriptionListener(this);
                sessionActivityListeners.forEach(l -> client.removeSessionActivityListener(l));
                retryDelegate.completeOrRetry(DISCONNECT, this::getDisconnectPeriod, client::disconnect);
            } catch (OPCUAException ex) {
                log.debug("Disconnection failed with exception: ", ex);
                log.error("Error disconnecting from endpoint with uri {}: ", uri);
            }
        } else {
            log.info("Client not connected, skipping disconnection attempt.");
        }
        sessionActivityListeners.clear();
        subscriptionMap.clear();
        disconnectedOn.set(-1);
        updateEquipmentStateOnSessionChanges = false;
        log.info("Completed disconnecting endpoint {}", uri);
    }

    /**
     * Add a list of item definitions as monitored items to a subscription with the number of retries configured in
     * {@link cern.c2mon.daq.opcua.config.AppConfigProperties} in case of connection faults and notify the {@link
     * MessageSender} of all future value updates
     * @param group       The {@link SubscriptionGroup} to add the {@link ItemDefinition}s to
     * @param definitions the {@link ItemDefinition}s for which to create monitored items
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws OPCUAException of type {@link ConfigurationException} or {@link EndpointDisconnectedException}.
     */
    @Override
    public Map<Integer, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) throws OPCUAException {
        try {
            log.info("Subscribing definitions with publishing interval {}.", group.getPublishInterval());
            return subscribeWithCallback(group.getPublishInterval(), definitions, this::defaultSubscriptionCallback);
        } catch (ConfigurationException | EndpointDisconnectedException e) {
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

    @Override
    public void recreateAllSubscriptions() throws CommunicationException {
        final Collection<SubscriptionGroup> groups = mapper.getGroups()
                .stream()
                .filter(g -> g.size() > 0)
                .collect(toList());
        boolean anySuccess = groups.isEmpty();
        for (SubscriptionGroup group : groups) {
            try {
                // if at least one group could be subscribed, the operation is considered to have been a success.
                anySuccess = anySuccess || resubscribeGroupsAndReportSuccess(group);
            } catch (EndpointDisconnectedException e) {
                log.debug("Failed with exception: ", e);
                log.info("Session was closed, abort subscription recreation process.");
                return;
            } catch (OPCUAException e) {
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
     * Subscribes to a {@link NodeId} with the itemCreationCallback  with the number of retries configured in {@link
     * cern.c2mon.daq.opcua.config.AppConfigProperties} in case of connection faults.
     * @param publishingInterval   the publishing interval of the subscription the definitions are added to.
     * @param definitions          the {@link ItemDefinition}s containing the {@link NodeId}s to subscribe to
     * @param itemCreationCallback the callback to apply upon creation of the items in the subscription
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws OPCUAException if the definitions could not be subscribed.
     */
    public Map<Integer, SourceDataTagQuality> subscribeWithCallback(int publishingInterval,
                                                                    Collection<ItemDefinition> definitions,
                                                                    Consumer<UaMonitoredItem> itemCreationCallback) throws OPCUAException {
        UaSubscription subscription = getOrCreateSubscription(publishingInterval);
        List<MonitoredItemCreateRequest> requests = definitions.stream()
                .map(this::toMonitoredItemCreateRequest)
                .collect(toList());
        return retryDelegate.completeOrRetry(CREATE_MONITORED_ITEM, this::getDisconnectPeriod,
                () -> subscription.createMonitoredItems(TimestampsToReturn.Both, requests, (item, i) -> itemCreationCallback.accept(item)))
                .stream()
                .collect(toMap(i -> i.getClientHandle().intValue(), i -> MiloMapper.getDataTagQuality(i.getStatusCode())));
    }

    /**
     * Delete an item from an OPC UA subscription  with the number of retries configured in {@link
     * cern.c2mon.daq.opcua.config.AppConfigProperties} in case of connection faults. If the subscription contains only
     * the one item, the whole subscription is deleted.
     * @param clientHandle    the identifier of the monitored item to remove.
     * @param publishInterval the publishing interval subscription to remove the monitored item from.
     * @return whether the removal from subscription could be completed successfully. False if no item with the
     * clientHandle was previously subscribed.
     */
    @Override
    public boolean deleteItemFromSubscription(int clientHandle, int publishInterval) {
        final UaSubscription subscription = subscriptionMap.get(publishInterval);
        if (subscription == null) {
            log.info("Item cannot be mapped to a subscription. Skipping deletion.");
            return false;
        }
        try {
            if (subscription.getMonitoredItems().size() <= 1) {
                deleteSubscription(publishInterval);
                return true;
            }
            List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems().stream()
                    .filter(i -> i.getClientHandle().intValue() == clientHandle)
                    .collect(toList());
            final List<StatusCode> statusCodes = retryDelegate.completeOrRetry(
                    DELETE_MONITORED_ITEM,
                    this::getDisconnectPeriod,
                    () -> subscription.deleteMonitoredItems(itemsToRemove));
            return statusCodes.stream().allMatch(StatusCode::isGood);
        } catch (OPCUAException ex) {
            log.error("Tag with ID {} could not be completed successfully on endpoint {}.", mapper.getTagId(clientHandle), getUri(), ex);
            return false;
        }
    }

    /**
     * Read the current value from a node on the currently connected OPC UA server  with the number of retries
     * configured in {@link cern.c2mon.daq.opcua.config.AppConfigProperties} in case of connection faults.
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link DataValue} containing the value read from the node.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        final DataValue value = retryDelegate.completeOrRetry(READ, this::getDisconnectPeriod,
                () -> client.readValue(0, TimestampsToReturn.Both, nodeId));
        if (value == null) {
            throw new ConfigurationException(READ);
        }
        return new AbstractMap.SimpleEntry<>(
                new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime()),
                MiloMapper.getDataTagQuality(value.getStatusCode()));
    }

    /**
     * Write a value to a node on the currently connected OPC UA server with the number of retries configured in {@link
     * cern.c2mon.daq.opcua.config.AppConfigProperties} in case of connection faults.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write action finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        // Many OPC UA Servers are unable to deal with StatusCode or DateTime, hence set to null
        DataValue dataValue = new DataValue(new Variant(value), null, null);
        final StatusCode statusCode = retryDelegate.completeOrRetry(WRITE, this::getDisconnectPeriod, () -> client.writeValue(nodeId, dataValue));
        log.info("Writing value {} to node {} yielded status code {}.", value, nodeId, statusCode);
        return statusCode.isGood();
    }

    /**
     * Call the method node associated with the definition  with the number of retries configured in {@link
     * cern.c2mon.daq.opcua.config.AppConfigProperties} in case of connection faults. It no method node is specified
     * within the definition, it is assumed that the primary node is the method node, and the first object node ID
     * encountered during browse is used as object node.
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
        return retryDelegate.completeOrRetry(SERVER_NODE, this::getDisconnectPeriod,
                () -> client.getAddressSpace().getObjectNode(Identifiers.Server_ServerRedundancy, ServerRedundancyTypeNode.class));
    }

    /**
     * Called when the client is connected to the server and the session activates.
     * @param session the current session
     */
    @Override
    public void onSessionActive(UaSession session) {
        if (updateEquipmentStateOnSessionChanges) {
            messageSender.onEquipmentStateUpdate(OK);
        }
        disconnectedOn.getAndUpdate(l -> Math.min(l, 0L));
    }

    /**
     * Called when the client is disconnected from the server and the session deactivates.
     * @param session the current session
     */
    @Override
    public void onSessionInactive(UaSession session) {
        log.info("Session deactivated");
        if (updateEquipmentStateOnSessionChanges) {
            messageSender.onEquipmentStateUpdate(CONNECTION_LOST);
        }
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
        final BrowseResult result = retryDelegate.completeOrRetry(BROWSE, this::getDisconnectPeriod, () -> client.browse(bd));
        if (result.getReferences() != null && result.getReferences().length > 0) {
            final Optional<NodeId> objectNode = result.getReferences()[0].getNodeId().local(client.getNamespaceTable());
            if (result.getStatusCode().isGood() && objectNode.isPresent()) {
                return objectNode.get();
            }
        }
        throw new ConfigurationException(OBJ_INVALID);
    }

    /**
     * Delete an existing subscription with a certain number of retries in case of connection error.
     * @param timeDeadband The timeDeadband of the subscription to delete along with all contained MonitoredItems.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    private void deleteSubscription(int timeDeadband) throws OPCUAException {
        final UaSubscription subscription = subscriptionMap.remove(timeDeadband);
        if (subscription != null) {
            retryDelegate.completeOrRetry(DELETE_SUBSCRIPTION, this::getDisconnectPeriod,
                    () -> client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
        }
    }

    private boolean resubscribeGroupsAndReportSuccess(SubscriptionGroup group) throws OPCUAException {
        final Map<Integer, SourceDataTagQuality> handleQualityMap = subscribe(group, group.getTagIds().values());
        return handleQualityMap != null && handleQualityMap.values().stream().anyMatch(SourceDataTagQuality::isValid);
    }

    private Map.Entry<Boolean, Object[]> callMethod(NodeId objectId, NodeId methodId, Object arg) throws OPCUAException {
        final Variant[] variants = arg == null ? null : new Variant[]{new Variant(arg)};
        final CallMethodResult result = retryDelegate.completeOrRetry(METHOD, this::getDisconnectPeriod,
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
                config.exceptionClassifierTemplate().execute(retryContext -> {
                    if (!resubscribeGroupsAndReportSuccess(group)) {
                        throw new CommunicationException(CREATE_SUBSCRIPTION);
                    }
                    return null;
                });
            } catch (OPCUAException e) {
                log.error("Subscription recreation aborted: ", e);
            }
        } else {
            log.info("The subscription cannot be recreated, since it cannot be associated with any DataTags.");
        }
    }

    private UaSubscription getOrCreateSubscription(int timeDeadband) throws OPCUAException {
        UaSubscription subscription = subscriptionMap.get(timeDeadband);
        if (subscription == null || !client.getSubscriptionManager().getSubscriptions().contains(subscription)) {
            subscription = retryDelegate.completeOrRetry(CREATE_SUBSCRIPTION, this::getDisconnectPeriod,
                    () -> client.getSubscriptionManager().createSubscription(timeDeadband));
            subscriptionMap.put(timeDeadband, subscription);
        }
        return subscription;
    }

    private void defaultSubscriptionCallback(UaMonitoredItem item) {
        final Long tagId = mapper.getTagId(item.getClientHandle().intValue());
        if (tagId == null) {
            log.info("Receives a value update that could not be associated with a DataTag.");
        } else {
            item.setValueConsumer(value -> dataValueToValueUpdate(value, tagId));
        }
    }

    private void dataValueToValueUpdate(DataValue value, Long tagId) {
         if (value == null) {
            log.info("Received a null update.");
        } else {
            final StatusCode statusCode = (value.getStatusCode() == null) ? StatusCode.BAD : value.getStatusCode();
            SourceDataTagQuality tagQuality = MiloMapper.getDataTagQuality(statusCode);
            ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()));
            final Long sourceTime = value.getSourceTime() == null ? null : value.getSourceTime().getJavaTime();
            final Long serverTime = value.getServerTime() == null ? null : value.getServerTime().getJavaTime();
            final Long recordedTime = config.getTimeRecordMode().getTime(sourceTime, serverTime);
            if (recordedTime != null) {
                valueUpdate.setSourceTimestamp(recordedTime);
            }
            messageSender.onValueUpdate(tagId, tagQuality, valueUpdate);
        }
    }

    private MonitoredItemCreateRequest toMonitoredItemCreateRequest(ItemDefinition definition) {
        // If the samplingInterval is set to 0, the source will provide updates at the fastest possible rate.
        double samplingInterval = 0;

        DataChangeFilter filter = new DataChangeFilter(DataChangeTrigger.StatusValue,
                uint(definition.getValueDeadbandType().getOpcuaValueDeadbandType()),
                (double) definition.getValueDeadband());
        MonitoringParameters mp = new MonitoringParameters(UInteger.valueOf(definition.getClientHandle()),
                samplingInterval,
                ExtensionObject.encode(client.getSerializationContext(), filter),
                uint(config.getQueueSize()),
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