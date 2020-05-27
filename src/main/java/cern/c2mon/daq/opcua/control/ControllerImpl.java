/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.*;

@Component("controller")
@Slf4j
public class ControllerImpl implements Controller {

    @Setter
    private Endpoint endpoint;

    private final TagSubscriptionMapper mapper;

    private final EndpointListener endpointListener;

    @Autowired
    @Setter
    private AppConfig config;

    private String uri;

    AtomicBoolean triggerReconnection = new AtomicBoolean(false);

    public ControllerImpl(Endpoint endpoint, TagSubscriptionMapper mapper, EndpointListener endpointListener) {
        this.endpoint = endpoint;
        this.mapper = mapper;
        this.endpointListener = endpointListener;
    }

    /**
     * Connect to an OPC UA server.
     *
     * @param uri the URI of the OPC UA server to connect to
     * @throws OPCUAException if it is not possible to connect to any of the the OPC UA server's endpoints with
     *                                the given authentication configuration settings and within the number of retries specified.
     */
    @Override
    public void connect(String uri) throws OPCUAException {
        this.uri = uri;
        try {
            config.initialRetryTemplate().execute(retryContext -> {
                log.info("Connection attempt {}/{}... ", retryContext.getRetryCount() + 1, config.getMaxInitialRetryAttempts());
                connect();
                triggerReconnection.set(true);
                return null;
            });
        } catch (OPCUAException e) {
            endpointListener.onEquipmentStateUpdate(CONNECTION_FAILED);
            throw e;
        }
    }

    @Override
    public void reconnect() {
        if (triggerReconnection.get() && endpoint.isConnected()) {
            log.info("Triggering client restart");

            // store the current subscription configuration in case it changed from the initial one through the IDataTagChanger interface
            final var currentConfig = new ConcurrentHashMap<>(mapper.getTagIdDefinitionMap());
            synchronized (this) {
                stop();
                triggerReconnection.set(true);
            }

            try {
                config.retryTemplate().execute(retryContext -> {
                    log.info("Reconnection attempt {}/{}... ", retryContext.getRetryCount() + 1, config.getMaxRetryAttempts());
                    connect();
                    return null;
                });

                mapper.addToTagDefinitionMap(currentConfig);
                mapper.mapToGroups(currentConfig.values())
                        .forEach((group, definitions) -> this.subscribeToGroup(group, definitions)
                                .exceptionally(t -> {
                                    log.error("Could not resubscribe Tags with time deadband {}.", group.getPublishInterval());
                                    return null;
                                }).join());
                refreshAllDataTags();
                triggerReconnection.set(false);
            } catch (OPCUAException e) {
                //TODO: what if we can't connect within max attempts?
            }
        } else if (triggerReconnection.get()) {
            log.info("Client is connected, skipping restart.");
        } else {
            log.info("Controller was intentionally shut down, don't restart.");
        }
    }

    @Override
    public void stop () {
        triggerReconnection.set(false);
        mapper.clear();
        if (isConnected()) {
            endpoint.disconnect()
                    .exceptionally(t -> {
                        log.error("Error disconnecting: ", t);
                        return null;
                    }).join();
        }
    }

    @Override
    public synchronized boolean isConnected () {
        return endpoint != null && endpoint.isConnected();
    }

    @Override
    public synchronized void subscribeTags (@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        if (dataTags.isEmpty()) {
            throw new ConfigurationException(ConfigurationException.Cause.DATATAGS_EMPTY);
        }
        CompletableFuture.allOf(
                mapper.mapTagsToGroupsAndDefinitions(dataTags).entrySet()
                .stream()
                .map(e -> subscribeToGroup(e.getKey(), e.getValue()))
                .toArray(CompletableFuture[]::new))
                .exceptionally(t -> {
                    log.error("Could not subscribe all Tags");
                    return null;
                }).join();
    }

    @Override
    public synchronized void refreshAllDataTags () {
        CompletableFuture.allOf(
                mapper.getTagIdDefinitionMap().entrySet().stream()
                        .map(e -> refresh(e.getKey(), e.getValue().getNodeId()))
                        .toArray(CompletableFuture[]::new))
                .exceptionally(t -> {
                    log.error("An exception occurred when refreshing all data tags: ", t);
                    return null;
                }).join();
    }

    @Override
    public synchronized void refreshDataTag (ISourceDataTag sourceDataTag) {
        refresh(sourceDataTag.getId(), mapper.getOrCreateDefinition(sourceDataTag).getNodeId());
    }

    @Override
    public synchronized CompletableFuture<Void> writeAlive(final OPCHardwareAddress address, final Object value) {
        NodeId nodeId = ItemDefinition.toNodeId(address);
        return endpoint.write(nodeId, value)
                .thenAccept(endpointListener::onAlive);
    }

    @Override
    public CompletableFuture<Void> recreateSubscription (UaSubscription subscription) {
        return endpoint.deleteSubscription(subscription)
                .exceptionally(t -> {
                    log.error("Could not delete subscription. Proceed with recreation. ", t);
                    return null;
                }).thenCompose(s -> {
                    final List<DataTagDefinition> definitions = subscription.getMonitoredItems().stream()
                            .map(item -> {
                                final UInteger clientHandle = item.getClientHandle();
                                final Long tagId = mapper.getTagId(clientHandle);
                                return mapper.getDefinition(tagId);
                            }).collect(Collectors.toList());
                    if (definitions.isEmpty()) {
                        throw new CompletionException(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION));
                    }
                    final SubscriptionGroup group = mapper.getOrCreateGroup(definitions.get(0).getTimeDeadband());
                    group.reset();
                    return subscribeToGroup(group, definitions);
                }).thenApply(success -> {
                    if (!success) {
                        throw new CompletionException(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION));
                    }
                    log.info("Subscription recreated.");
                    return null;
                });
    }


    private void connect() throws CommunicationException, ConfigurationException {
        endpoint.initialize(uri);
    }

    @Override
    public synchronized CompletableFuture<Boolean> subscribeTag (@NonNull final ISourceDataTag sourceDataTag) {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        DataTagDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);
        return subscribeToGroup(group, Collections.singletonList(definition));
    }

    /**
     * Removes a Tag from the internal configuration and if already subscribed from the OPC UA subscription. It the subscription is then empty, it is deleted as well.
     * @param dataTag the tag to remove
     * @return true if the tag was previously known.
     */
    @Override
    public synchronized CompletableFuture<Boolean> removeTag(final ISourceDataTag dataTag) {
        SubscriptionGroup group = mapper.getGroup(dataTag);
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
        if (group.isSubscribed() && group.contains(dataTag)) {
            log.info("Unsubscribing tag from server.");
            UaSubscription subscription = group.getSubscription();
            future = endpoint.deleteItemFromSubscription(mapper.getDefinition(dataTag.getId()).getClientHandle(), subscription)
                    .thenApply((o) -> {
                        if (group.size() < 1) {
                            log.info("Subscription is empty. Deleting subscription.");
                            endpoint.deleteSubscription(subscription);
                        }
                        return true;
                    });
        }
        return future.thenApply(s -> {
            final boolean removeTag = mapper.removeTag(dataTag);
            if (group.size() < 1) {
                group.reset();
            }
            return removeTag;
        });
    }


    private CompletableFuture<Void> refresh(long tagId, NodeId address) {
        return endpoint.read(address)
                .thenAccept(value -> notifyListener(tagId, value))
                .exceptionally(t -> {
                    log.error("An error occurred refreshing tag with id {}.", tagId, t);
                    return null;
                });
    }

    private CompletableFuture<Boolean> subscribeToGroup (SubscriptionGroup group, List<DataTagDefinition> definitions) {
        CompletableFuture<Void> createSubscription = CompletableFuture.completedFuture(null);
        if (!group.isSubscribed() || !endpoint.containsSubscription(group.getSubscription())) {
            createSubscription = endpoint
                    .createSubscription(group.getPublishInterval())
                    .thenAccept(group::setSubscription);
        }
        return createSubscription
                .thenCompose(v -> endpoint.subscribeItem(group.getSubscription(), definitions, this::itemCreationCallback)
                .thenApply(this::completeSubscription));
    }

    private boolean completeSubscription (List<UaMonitoredItem> monitoredItems) {
        boolean allSuccessful = true;
        for (UaMonitoredItem item : monitoredItems) {
            final Long tagId = mapper.getTagId(item.getClientHandle());
            final var statusCode = item.getStatusCode();
            if (statusCode.isBad()) {
                allSuccessful = false;
                final var qualityCode = MiloMapper.getDataTagQualityCode(statusCode);
                endpointListener.onTagInvalid(tagId, new SourceDataTagQuality(qualityCode));
            } else {
                try {
                    mapper.addTagToGroup(tagId);
                } catch (Exception e) {
                    // Stems from a subscription recreation and can be ignored
                }
            }
        }
        return allSuccessful;
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        Long tag = mapper.getTagId(item.getClientHandle());
        item.setValueConsumer(value -> notifyListener(tag, value));
    }

    private void notifyListener(Long tagId, DataValue value) {
        SourceDataTagQualityCode tagQuality = MiloMapper.getDataTagQualityCode(value.getStatusCode());
        ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime());

        if (!tagQuality.equals(SourceDataTagQualityCode.OK)) {
            endpointListener.onTagInvalid(tagId, new SourceDataTagQuality(tagQuality));
        } else {
            endpointListener.onNewTagValue(tagId, valueUpdate, new SourceDataTagQuality(tagQuality));
        }
    }
}
