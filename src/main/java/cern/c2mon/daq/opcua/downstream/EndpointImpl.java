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
package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState.*;

@Slf4j
@AllArgsConstructor
public class EndpointImpl implements Endpoint {

    @Setter
    private MiloClientWrapper client;
    private TagSubscriptionMapper mapper;
    private EventPublisher events;

    public void initialize (boolean connectionLost) {
        if (connectionLost) {
            events.notifyEquipmentState(CONNECTION_LOST);
        }
        try {
            client.initialize();
            client.connect();
            events.notifyEquipmentState(OK);
        } catch (ExecutionException | InterruptedException e) {
            events.notifyEquipmentState(CONNECTION_FAILED);
            throw new OPCCommunicationException(CONNECTION_FAILED.message, e);
        }
    }

    @Override
    public void recreateSubscription (UaSubscription subscription) {
        client.deleteSubscription(subscription)
                .thenCompose(aVoid -> {
                    SubscriptionGroup group = mapper.getGroup(subscription);
                    List<ItemDefinition> definitions = group.getDefinitions();
                    group.reset();
                    return subscribeToGroup(group, definitions);
                })
                .exceptionally(e -> {throw new OPCCommunicationException("Could not recreate subscription", e);});
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTags (@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException, OPCCommunicationException {
        if (dataTags.isEmpty()) {
            throw new ConfigurationException(ConfigurationException.Cause.DATATAGS_EMPTY);
        }
        return CompletableFuture.allOf(mapper.maptoGroupsWithDefinitions(dataTags).entrySet()
                .stream()
                .map(e -> subscribeToGroup(e.getKey(), e.getValue()))
                .toArray(CompletableFuture[]::new))
                .exceptionally(e -> {throw new OPCCommunicationException("Could not subscribe Tags", e);});
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTag (@NonNull final ISourceDataTag sourceDataTag) throws OPCCommunicationException {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        ItemDefinition definition = mapper.getDefinition(sourceDataTag);

        return subscribeToGroup(group, Collections.singletonList(definition))
                .exceptionally(e -> {throw new OPCCommunicationException("Could not subscribe Tags", e);});
    }

    private CompletableFuture<Void> subscribeToGroup (SubscriptionGroup group, List<ItemDefinition> definitions) {
        UaSubscription subscription = (group.isSubscribed()) ?
                group.getSubscription() :
                client.createSubscription(group.getDeadband().getTime());
        group.setSubscription(subscription);

        return client.subscribeItemDefinitions(subscription, definitions, group.getDeadband(), this::itemCreationCallback)
                    .thenAccept(this::subscriptionCompletedCallback);
    }

    @Override
    public synchronized CompletableFuture<Void> removeDataTag (final ISourceDataTag dataTag) throws IllegalArgumentException {
        SubscriptionGroup subscriptionGroup = mapper.getGroup(dataTag);

        if (!subscriptionGroup.isSubscribed()) {
            throw new IllegalArgumentException("The tag cannot be removed, since the subscription group is not subscribed.");
        }

        UaSubscription subscription = subscriptionGroup.getSubscription();
        UInteger clientHandle = mapper.getDefinition(dataTag).getClientHandle();

        CompletableFuture<Void> future = client.deleteItemFromSubscription(clientHandle, subscription)
                .thenRun(() -> mapper.removeTagFromGroup(dataTag));
        
        if (subscriptionGroup.size() < 1) {
            return future.thenCompose(aVoid -> removeSubscription(subscriptionGroup, subscription));
        }
        return future;
    }

    private CompletableFuture<Void> removeSubscription (SubscriptionGroup subscriptionGroup, UaSubscription subscription) {
        subscriptionGroup.setSubscription(null);
        return client.deleteSubscription(subscription);
    }

    @Override
    public synchronized void refreshDataTags (final Collection<ISourceDataTag> dataTags) {
        for (ISourceDataTag dataTag : dataTags) {
            NodeId address = mapper.getDefinition(dataTag).getAddress();
            client.read(address).thenAccept(dataValues -> readCallback(dataValues, dataTag));
        }
    }

    @Override
    public synchronized CompletableFuture<Void> reset () {
        mapper.clear();
        return CompletableFuture.runAsync(() -> client.disconnect());
    }

    @Override
    public synchronized CompletableFuture<StatusCode> write (final OPCHardwareAddress address, final Object value) {
        NodeId nodeId = ItemDefinition.toNodeId(address);
        DataValue dataValue = new DataValue(new Variant(value));
        return client.write(nodeId, dataValue);
    }

    @Override
    public synchronized boolean isConnected () {
        return client.isConnected();
    }

    private void readCallback (List<DataValue> dataValues, ISourceDataTag tag) {
        for (DataValue value : dataValues) {
            events.notifyTagEvent(value.getStatusCode(), tag, value);
        }
    }

    private void subscriptionCompletedCallback (List<UaMonitoredItem> monitoredItems) {
        for (UaMonitoredItem item : monitoredItems) {
            ISourceDataTag dataTag = mapper.getTag(item.getClientHandle());
            if (item.getStatusCode().isBad()) {
                events.invalidTag(dataTag);
            }
            else {
                mapper.addTagToGroup(dataTag);
            }
        }
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        ISourceDataTag tag = mapper.getTag(item.getClientHandle());
        item.setValueConsumer(value -> events.notifyTagEvent(item.getStatusCode(), tag, value));
    }
}
