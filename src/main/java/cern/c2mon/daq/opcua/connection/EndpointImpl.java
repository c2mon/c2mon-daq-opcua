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
package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.GroupDefinitionPair;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.var;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState.CONNECTION_FAIL;
import static cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState.OK;

@Slf4j
@AllArgsConstructor
public class EndpointImpl implements Endpoint {

    @Setter
    private MiloClientWrapper client;
    private TagSubscriptionMapper mapper;
    private EventPublisher events;

    public synchronized void reconnect () {
        reset().thenRun(this::initialize); //TODO add restart functionality
    }

    public synchronized void initialize () {
        try {
            client.initialize();
            events.notifyEquipmentState(OK);
        } catch (ExecutionException | InterruptedException e) {
            events.notifyEquipmentState(CONNECTION_FAIL);
            throw new OPCCommunicationException(CONNECTION_FAIL.message, e);
        }
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTags (@NonNull final Collection<ISourceDataTag> dataTags) {
        if (dataTags.isEmpty()) {
            throw new IllegalArgumentException("No data tags to subscribe.");
        }
        return CompletableFuture.allOf(mapper.toGroups(dataTags).entrySet()
                .stream()
                .map(e -> subscribeToGroup(e.getKey(), e.getValue()))
                .toArray(CompletableFuture[]::new))
                .exceptionally(e -> {throw new OPCCommunicationException("Could not subscribe Tags", e);});
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTag (@NonNull final ISourceDataTag sourceDataTag) throws OPCCommunicationException {
        var pair = mapper.toGroup(sourceDataTag);
        return subscribeToGroup(pair.getSubscriptionGroup(), Collections.singletonList(pair.getItemDefinition()))
                .exceptionally(e -> {throw new OPCCommunicationException("Could not subscribe Tags", e);});
    }

    private CompletableFuture<Void> subscribeToGroup (SubscriptionGroup group, List<ItemDefinition> definitions) {
        UaSubscription subscription = (group.isSubscribed()) ? group.getSubscription() : createSubscription(group);
        group.setSubscription(subscription);

        return client.subscribeItemDefinitions(subscription, definitions, group.getDeadband(), this::itemCreationCallback)
                    .thenAccept(this::subscriptionCompletedCallback);
    }

    private UaSubscription createSubscription (SubscriptionGroup group) {
        return client.createSubscription(group.getDeadband().getTime());
    }

    @Override
    public synchronized CompletableFuture<Void> removeDataTag (final ISourceDataTag dataTag) throws IllegalArgumentException {
        GroupDefinitionPair pair = mapper.toGroup(dataTag);
        SubscriptionGroup subscriptionGroup = pair.getSubscriptionGroup();

        if (!subscriptionGroup.isSubscribed()) {
            throw new IllegalArgumentException("The tag cannot be removed, since the subscription group is not subscribed.");
        }

        UaSubscription subscription = subscriptionGroup.getSubscription();
        UInteger clientHandle = pair.getItemDefinition().getClientHandle();

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
        events.clear();
        mapper.clear();
        return CompletableFuture.runAsync(() -> client.disconnect());
    }

    @Override
    public synchronized void write (
            final OPCHardwareAddress address, final Object value) {
//        ItemDefinition itemDefinition = ItemDefinitionFactory.createItemDefinition(1L, address);
//        if (itemDefinition != null) {
//            onWrite(itemDefinition, value);
//        }
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
            ISourceDataTag dataTag = mapper.getTagBy(item.getClientHandle());
            if (item.getStatusCode().isBad()) {
                events.invalidTag(dataTag);
            }
            else {
                mapper.addTagToGroup(dataTag);
            }
        }
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        ISourceDataTag tag = mapper.getTagBy(item.getClientHandle());
        item.setValueConsumer(value -> events.notifyTagEvent(item.getStatusCode(), tag, value));
    }
}
