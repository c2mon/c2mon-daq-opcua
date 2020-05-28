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

import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component("controller")
@Slf4j
@RequiredArgsConstructor
public class ControllerImpl implements Controller {

    private final TagSubscriptionMapper mapper;
    private final EndpointListener endpointListener;
    private final Endpoint endpoint;
    private final AtomicBoolean stopped = new AtomicBoolean(true);

    /**
     * Connect to an OPC UA server.
     * @param uri the URI of the OPC UA server to connect to
     * @throws ConfigurationException if it is not possible to connect to any of the the OPC UA server's endpoints with
     *                                the given authentication configuration settings
     * @throws CommunicationException if it is not possible to connect to the OPC UA server within the configured number
     *                                of tries
     */
    public void connect(String uri) throws OPCUAException, InterruptedException {
        try {
            endpoint.initialize(uri);
            stopped.set(false);
        } catch (OPCUAException e) {
            endpointListener.onEquipmentStateUpdate(EndpointListener.EquipmentState.CONNECTION_FAILED);
            throw e;
        }
    }
/*
    @Override
    public void reconnect() {
        if (!stopped.get() && endpoint.isConnected()) {
            log.info("Triggering client restart");

            // store the current subscription configuration in case it changed from the initial one through the IDataTagChanger interface
            final var currentConfig = new ConcurrentHashMap<>(mapper.getTagIdDefinitionMap());
            synchronized (this) {
                stop();
                stopped.set(false);
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
                stopped.set(false);
            } catch (OPCUAException e) {
                //TODO: what if we can't connect within max attempts?
            }
        } else if (endpoint.isConnected()) {
            log.info("Client is connected, skipping restart.");
        } else {
            log.info("Controller was intentionally shut down, don't restart.");
        }
    }
*/

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    @Override
    public void stop() {
        stopped.set(true);
        mapper.clear();
        endpoint.disconnect()
                .exceptionally(t -> {
                    log.error("Error disconnecting: ", t);
                    return null;
                }).join();
    }

    /**
     * Returns whether the controller has been intentionally shut down.
     * @return true if the controller's stop method has been called, or the initial connection was not successful.
     */
    @Override
    public boolean wasStopped() {
        return stopped.get();
    }

    /**
     * Subscribes to the OPC UA nodes corresponding to the data tags on the server.
     * @param dataTags the collection of ISourceDataTags to subscribe to
     * @throws ConfigurationException if the collection of tags was empty.
     */
    @Override
    public synchronized void subscribeTags(@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException {
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

    /**
     * Subscribes to the OPC UA node corresponding to one data tag on the server.
     * @param sourceDataTag the ISourceDataTag to subscribe to
     * @return A completable future indicating whether the Tag has been subscribed successfully.
     */
    @Override
    public synchronized CompletableFuture<Boolean> subscribeTag(@NonNull final ISourceDataTag sourceDataTag) {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        DataTagDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);
        return subscribeToGroup(group, Collections.singletonList(definition));
    }

    /**
     * Removes a Tag from the internal configuration and if already subscribed from the OPC UA subscription. It the
     * subscription is then empty, it is deleted as well.
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

    /**
     * Reads the current values from the server for all subscribed data tags.
     */
    @Override
    public synchronized void refreshAllDataTags() {
        CompletableFuture.allOf(
                mapper.getTagIdDefinitionMap().entrySet().stream()
                        .map(e -> refresh(e.getKey(), e.getValue().getNodeId()))
                        .toArray(CompletableFuture[]::new))
                .exceptionally(t -> {
                    log.error("An exception occurred when refreshing all data tags: ", t);
                    return null;
                }).join();
    }

    /**
     * Reads the sourceDataTag's current value from the server
     * @param sourceDataTag the tag whose current value to read
     */
    @Override
    public synchronized void refreshDataTag(ISourceDataTag sourceDataTag) {
        refresh(sourceDataTag.getId(), mapper.getOrCreateDefinition(sourceDataTag).getNodeId())
                .exceptionally(t -> {
                    log.error("An exception ocurred refreshing data tag {}.", sourceDataTag);
                    return null;
                }).join();
    }

    /**
     * Called when a subscription could not be automatically transferred in between sessions. In this case, the
     * subscription is recreated from scratch.
     * @param subscription the subscription of the old session which must be recreated
     * @return a completable future which completes successfully if recreation was successful.
     */
    @Override
    public CompletableFuture<Void> recreateSubscription(UaSubscription subscription) {
        return endpoint.deleteSubscription(subscription)
                .exceptionally(t -> {
                    log.error("Could not delete subscription. Proceed with recreation. ", t);
                    return null;
                }).thenCompose(s -> {
                    final List<DataTagDefinition> definitions = subscription.getMonitoredItems().stream()
                            .map(item -> {
                                final Long tagId = mapper.getTagId(item.getClientHandle());
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


    private CompletableFuture<Void> refresh(long tagId, NodeId address) {
        return endpoint.read(address)
                .thenAccept(value -> notifyListener(tagId, value))
                .exceptionally(t -> {
                    log.error("An error occurred refreshing tag with id {}.", tagId, t);
                    return null;
                });
    }

    private CompletableFuture<Boolean> subscribeToGroup(SubscriptionGroup group, List<DataTagDefinition> definitions) {
        CompletableFuture<Void> createSubscription = CompletableFuture.completedFuture(null);
        if (!group.isSubscribed() || !endpoint.isCurrent(group.getSubscription())) {
            createSubscription = endpoint
                    .createSubscription(group.getPublishInterval())
                    .thenAccept(group::setSubscription);
        }
        return createSubscription
                .thenCompose(v -> endpoint.subscribeItem(group.getSubscription(), definitions, this::itemCreationCallback)
                        .thenApply(this::completeSubscription));
    }

    private boolean completeSubscription(List<UaMonitoredItem> monitoredItems) {
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

    private void itemCreationCallback(UaMonitoredItem item, Integer i) {
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
