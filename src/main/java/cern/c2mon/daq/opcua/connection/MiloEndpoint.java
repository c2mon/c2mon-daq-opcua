/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.connection;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.OK;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.BROWSE;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.CONNECT;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.CREATE_MONITORED_ITEM;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.CREATE_SUBSCRIPTION;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.DELETE_MONITORED_ITEM;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.DELETE_SUBSCRIPTION;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.DISCONNECT;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.METHOD;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.OBJ_INVALID;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.READ;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.SERVER_NODE;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.WRITE;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.DataChangeFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.OPCUANameSpaceIndex;
import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.EndpointDisconnectedException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import io.micrometer.core.annotation.Timed;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * An implementation of {@link Endpoint} relying on Eclipse Milo. Handles all interaction with a single server including
 * the the consolidation of security settings. Maintains a mapping in between the per-server time Deadbands and
 * associated subscription IDs. Informs the {@link MessageSender} of the state of connection and of new values. A
 * {@link CommunicationException} occurs when the method has been attempted as often and with a delay as configured
 * without success. A {@link LongLostConnectionException} is thrown when the connection has already been lost for the
 * time needed to execute all configured attempts. In this case, no retries are executed.
 * {@link ConfigurationException}s are never retried, and represent failures that require a configuration change to fix.
 * {@link EndpointDisconnectedException}s are never retried, as they indicate that a call is an asynchronously remaining
 * process of an endpoint that has been disconnected.
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Scope("prototype")
public class MiloEndpoint implements Endpoint, SessionActivityListener, UaSubscriptionManager.SubscriptionListener {

    public static final int TIMEOUT_SECONDS = 5;

    /**
     * Shows the instance of disconnection. 0 denotes that the endpoint is currently connected to the server, -1 that
     * the endpoint has been stopped.
     */
    private final AtomicLong disconnectedOn = new AtomicLong(0);
    private final SecurityModule securityModule;
    private final TagSubscriptionReader mapper;
    private final MessageSender messageSender;
    private final AppConfigProperties properties;
    private final AppConfig config;

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
     * line with configuration options.
     * 
     * @param uri the server address to connect to.
     * @throws OPCUAException of type {@link CommunicationException} if it is not possible to connect to the OPC UA
     *             server within the configured number of attempts, and of type {@link ConfigurationException} if it is
     *             not possible to connect to any of the the OPC UA server's endpoints with the given authentication
     *             configuration settings.
     */
    @Override
    @Timed(value = "connect")
    public void initialize(String uri) throws OPCUAException {
        log.info("Initializing Endpoint at {}", uri);
        disconnectedOn.set(0);
        this.uri = uri;
        final Collection<EndpointDescription> endpoints = processSupplier(CONNECT,
                () -> DiscoveryClient.getEndpoints(uri));
        client = securityModule.createClient(uri, endpoints);
        client.addSessionActivityListener(this);
        sessionActivityListeners.add(this);
        final OpcUaSubscriptionManager subscriptionManager = client.getSubscriptionManager();
        subscriptionManager.addSubscriptionListener(this);
        subscriptionManager.resumeDelivery();
    }

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

    @Override
    public void disconnect() {
        log.info("Disconnecting endpoint at {}", uri);
        if (client != null) {
            try {
                client.getSubscriptionManager().clearSubscriptions();
                client.getSubscriptionManager().removeSubscriptionListener(this);
                sessionActivityListeners.forEach(l -> client.removeSessionActivityListener(l));
                retryOnConnection(DISCONNECT, client::disconnect);
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

    @Override
    public Map<Integer, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions)
            throws OPCUAException {
        try {
            log.info("Subscribing definitions with publishing interval {}.", group.getPublishInterval());
            return subscribeWithCallback(group.getPublishInterval(), definitions, this::defaultSubscriptionCallback);
        } catch (ConfigurationException | EndpointDisconnectedException e) {
            throw e;
        } catch (OPCUAException e) {
            final String ids = definitions.stream().map(d -> mapper.getTagId(d.getClientHandle()).toString())
                    .collect(Collectors.joining(", "));
            log.error("Tags with IDs {} could not be subscribed on endpoint with uri {}. ", ids, getUri(), e);
            return definitions.stream()
                    .map(definition -> new AbstractMap.SimpleEntry<>(definition.getClientHandle(),
                            new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    @Override
    public void recreateAllSubscriptions() throws CommunicationException {
        final Collection<SubscriptionGroup> groups = mapper.getGroups().stream().filter(g -> g.size() > 0)
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

    public Map<Integer, SourceDataTagQuality> subscribeWithCallback(int publishingInterval,
            Collection<ItemDefinition> definitions, Consumer<UaMonitoredItem> itemCreationCallback)
            throws OPCUAException {
        UaSubscription subscription = getOrCreateSubscription(publishingInterval);
        List<MonitoredItemCreateRequest> requests = definitions.stream().map(this::toMonitoredItemCreateRequest)
                .collect(toList());
        return retryOnConnection(CREATE_MONITORED_ITEM,
                () -> subscription.createMonitoredItems(TimestampsToReturn.Both, requests,
                        (item, i) -> itemCreationCallback.accept(item))).stream()
                                .collect(toMap(i -> i.getClientHandle().intValue(),
                                        i -> MiloMapper.getDataTagQuality(i.getStatusCode())));
    }

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
                    .filter(i -> i.getClientHandle().intValue() == clientHandle).collect(toList());
            final List<StatusCode> statusCodes = retryOnConnection(DELETE_MONITORED_ITEM,
                    () -> subscription.deleteMonitoredItems(itemsToRemove));
            return statusCodes.stream().allMatch(StatusCode::isGood);
        } catch (OPCUAException ex) {
            log.error("Tag with ID {} could not be completed successfully on endpoint {}.",
                    mapper.getTagId(clientHandle), getUri(), ex);
            return false;
        }
    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        final DataValue value = retryOnConnection(READ, () -> client.readValue(0, TimestampsToReturn.Both, nodeId));
        if (value == null) {
            throw new ConfigurationException(READ);
        }
        return new AbstractMap.SimpleEntry<>(MiloMapper.toValueUpdate(value, properties.getTimeRecordMode()),
                MiloMapper.getDataTagQuality(value.getStatusCode()));
    }

    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        // Many OPC UA Servers are unable to deal with StatusCode or DateTime, hence set to null
        DataValue dataValue = new DataValue(new Variant(value), null, null);
        final StatusCode statusCode = retryOnConnection(WRITE, () -> client.writeValue(nodeId, dataValue));
        log.info("Writing value {} to node {} yielded status code {}.", value, nodeId, statusCode);
        return statusCode.isGood();
    }

    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return (definition.getMethodNodeId() == null)
                ? callMethod(getParentObjectNodeId(definition.getNodeId()), definition.getNodeId(), arg)
                : callMethod(definition.getNodeId(), definition.getMethodNodeId(), arg);
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() throws OPCUAException {
        return retryOnConnection(SERVER_NODE, () -> client.getAddressSpace()
                .getObjectNode(Identifiers.Server_ServerRedundancy, ServerRedundancyTypeNode.class));
    }

    /**
     * Called when the client is connected to the server and the session activates.
     * 
     * @param session the current session
     */
    @Override
    public void onSessionActive(UaSession session) {
        log.info("Session activated");
        if (updateEquipmentStateOnSessionChanges) {
            messageSender.onEquipmentStateUpdate(OK);
        }
        disconnectedOn.getAndUpdate(l -> Math.min(l, 0L));
    }

    /**
     * Called when the client is disconnected from the server and the session deactivates.
     * 
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
     * 
     * @param subscription the subscription which times out
     * @param status the new status of the subscription
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
     * 
     * @param subscription the old subscription which could not be recreated regardless of the status code.
     * @param statusCode the code delivered by the server giving the reason of the subscription failure. It can be any
     *            of the codes "Bad_NotImplemented", "Bad_NotSupported", "Bad_OutOfService" or "Bad_ServiceUnsupported"
     */
    @Override
    public void onSubscriptionTransferFailed(UaSubscription subscription, StatusCode statusCode) {
        log.info("onSubscriptionTransferFailed event for {} : StatusCode {}", subscription.toString(), statusCode);
        recreate(subscription);
    }

    /**
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * 
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    private NodeId getParentObjectNodeId(NodeId nodeId) throws OPCUAException {
        final BrowseDescription bd = new BrowseDescription(nodeId, BrowseDirection.Inverse, Identifiers.References,
                true, uint(NodeClass.Object.getValue()), uint(BrowseResultMask.All.getValue()));
        final BrowseResult result = retryOnConnection(BROWSE, () -> client.browse(bd));
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
     * 
     * @param timeDeadband The timeDeadband of the subscription to delete along with all contained MonitoredItems.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    private void deleteSubscription(int timeDeadband) throws OPCUAException {
        final UaSubscription subscription = subscriptionMap.remove(timeDeadband);
        if (subscription != null) {
            retryOnConnection(DELETE_SUBSCRIPTION,
                    () -> client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
        }
    }

    private boolean resubscribeGroupsAndReportSuccess(SubscriptionGroup group) throws OPCUAException {
        final Map<Integer, SourceDataTagQuality> handleQualityMap = subscribe(group, group.getTagIds().values());
        return handleQualityMap != null && handleQualityMap.values().stream().anyMatch(SourceDataTagQuality::isValid);
    }

    private Map.Entry<Boolean, Object[]> callMethod(NodeId objectId, NodeId methodId, Object arg)
            throws OPCUAException {
        final Variant[] variants = arg == null ? null : new Variant[] { new Variant(arg) };
        final CallMethodResult result = retryOnConnection(METHOD,
                () -> client.call(new CallMethodRequest(objectId, methodId, variants)));
        final StatusCode statusCode = result.getStatusCode();
        log.info("Calling method {} on object {} returned status code {}.", methodId, objectId, statusCode);
        return new AbstractMap.SimpleEntry<>(statusCode.isGood(), MiloMapper.toObject(result.getOutputArguments()));
    }

    /**
     * Attempt to recreate the subscription periodically unless the subscription's monitored items cannot be mapped to
     * DataTags due to a configuration mismatch. Recreation attempts are discontinued when the thread is interrupted or
     * recreation finishes successfully.
     * 
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
                config.exceptionClassifierTemplate(properties).execute(retryContext -> {
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
            subscription = retryOnConnection(CREATE_SUBSCRIPTION,
                    () -> client.getSubscriptionManager().createSubscription(timeDeadband * 1000));
            // OPC UA publishing interval is given in milliseconds, see
            // https://reference.opcfoundation.org/v104/Core/docs/Part4/5.13.2/
            subscriptionMap.put(timeDeadband, subscription);
        }
        return subscription;
    }

    private void defaultSubscriptionCallback(UaMonitoredItem item) {
        final Long tagId = mapper.getTagId(item.getClientHandle().intValue());
        if (tagId == null) {
            log.info("Receives a value update that could not be associated with a DataTag.");
        } else {
            item.setValueConsumer(value -> {
                if (value == null) {
                    log.info("Received a null update.");
                } else {
                    final SourceDataTagQuality quality = MiloMapper.getDataTagQuality(value.getStatusCode());
                    final ValueUpdate valueUpdate = MiloMapper.toValueUpdate(value, properties.getTimeRecordMode());
                    messageSender.onValueUpdate(tagId, quality, valueUpdate);
                }
            });
        }
    }

    private MonitoredItemCreateRequest toMonitoredItemCreateRequest(ItemDefinition definition) {
        // If the samplingInterval is set to 0, the source will provide updates at the fastest possible rate.
        double samplingInterval = 0;

        DataChangeFilter filter = DataChangeFilter.builder().trigger(DataChangeTrigger.StatusValue) // Trigger if the
                                                                                                    // value's status
                                                                                                    // code or the value
                                                                                                    // itself changes
                .deadbandType(uint(definition.getValueDeadbandType().getValue()))
                .deadbandValue((double) definition.getValueDeadband()).build();
        MonitoringParameters mp = new MonitoringParameters(UInteger.valueOf(definition.getClientHandle()),
                samplingInterval, ExtensionObject.encode(client.getSerializationContext(), filter),
                uint(properties.getQueueSize()), true);
        ReadValueId id = new ReadValueId(definition.getNodeId(), AttributeId.Value.uid(), null,
                QualifiedName.NULL_VALUE);
        return new MonitoredItemCreateRequest(id, mode, mp);
    }

    private <T> T retryOnConnection(ExceptionContext context, Supplier<CompletableFuture<T>> futureSupplier)
            throws OPCUAException {
        return config.simpleRetryPolicy(properties).execute(retryContext -> {
            if (disconnectedOn.get() < 0) {
                log.info("Endpoint was stopped, cease retries.");
                throw new EndpointDisconnectedException(context);
            }
            return processSupplier(context, futureSupplier);
        });
    }

    private <T> T processSupplier(ExceptionContext context, Supplier<CompletableFuture<T>> futureSupplier)
            throws OPCUAException {
        try {
            return futureSupplier.get().get(properties.getRequestTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.debug("Execution {} failed with interrupted exception; ", context.name(), e);
            Thread.currentThread().interrupt();
            throw new EndpointDisconnectedException(context, e.getCause());
        } catch (ExecutionException | TimeoutException e) {
            log.debug("Execution {} failed with exception; ", context.name(), e);
            throw OPCUAException.of(context, e.getCause(), false);
        } catch (Exception e) {
            log.info("An unexpected exception occurred during {}.", context.name(), e);
            throw OPCUAException.of(context, e, false);
        }
    }

    /**
     * Asks the OPC UA server to provide index of namespaces, so that we can use the id instead of the name later on
     * 
     * @param addresses
     * @return
     * @throws EqIOException
     */
    @Override
    public void fillNameSpaceIndex() throws EqIOException {

        CompletableFuture<DataValue> future = this.client.readValue(0, TimestampsToReturn.Neither,
                Identifiers.Server_NamespaceArray);

        // convert the result into a map
        DataValue value;
        try {
            value = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            final Object rawValue = value.getValue().getValue();

            if (rawValue instanceof String[]) {
                final String[] namespaces = (String[]) rawValue;
                for (int i = 0; i < namespaces.length; i++) {
                    log.info("{} -> {}", namespaces[i], Unsigned.ushort(i));
                    OPCUANameSpaceIndex.get().addEntry(namespaces[i].toUpperCase(), i);
                }
            }
        } catch (Exception e) {
            throw new EqIOException(e);
        }

        log.info("Successfully connected.");
    }

}
