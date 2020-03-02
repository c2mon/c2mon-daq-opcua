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
import cern.c2mon.daq.opcua.mapping.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class EndpointImpl implements Endpoint {

    private final Collection<EndpointListener> listeners = new ConcurrentLinkedQueue<>();
    private MiloClientWrapper client;
    private TagSubscriptionMapper mapper;

    public EndpointImpl (TagSubscriptionMapper mapper, final MiloClientWrapper client) {
        this.mapper = mapper;
        this.client = client;
    }

    public synchronized void initialize () {
        try {
            client.initialize();
        } catch (ExecutionException | InterruptedException e) {
            throw new OPCCommunicationException("Could not connect to the client.", e);
        }
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTags (@NonNull final Collection<ISourceDataTag> dataTags) {
        if (dataTags.isEmpty()) {
            throw new IllegalArgumentException("No data tags to subscribe.");
        }

        return CompletableFuture.allOf(mapper
                .toGroups(dataTags)
                .entrySet()
                .stream()
                .map(e -> subscribeToGroup(e.getKey(), e.getValue()))
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public synchronized void subscribeTag (@NonNull final ISourceDataTag sourceDataTag) throws OPCCommunicationException {
        GroupDefinitionPair pair = mapper.toGroup(sourceDataTag);
        try {
            subscribeToGroup(pair.getSubscriptionGroup(), Collections.singletonList(pair.getItemDefinition())).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException("Could not subscribe to Tag.", e);
        }
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

        return removeItemFromSubscription(pair, subscriptionGroup).thenRun(() -> mapper.removeTagFromGroup(dataTag));
    }

    private CompletableFuture<Void> removeItemFromSubscription (GroupDefinitionPair pair, SubscriptionGroup subscriptionGroup) {
        UaSubscription subscription = subscriptionGroup.getSubscription();
        return subscriptionGroup.isEmpty() ?
                removeSubscription(subscriptionGroup, subscription) :
                client.deleteItemFromSubscription(pair.getItemDefinition().getClientHandle(), subscription);
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
    public void registerEndpointListener (final EndpointListener endpointListener) {
        listeners.add(endpointListener);
    }

    @Override
    public void unRegisterEndpointListener (final EndpointListener endpointListener) {
        listeners.remove(endpointListener);
    }

    @Override
    public synchronized CompletableFuture<Void> reset () {
        listeners.clear();
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

    private void readCallback (List<DataValue> dataValues, ISourceDataTag dataTag) {
        for (DataValue value : dataValues) {
            if (value.getStatusCode().isBad()) {
                notifyEndpointsAboutInvalidTag(dataTag);
            } else {
                notifyEndpointsAboutMonitoredItemChange(value, dataTag);
            }
        }
    }

    private void subscriptionCompletedCallback (List<UaMonitoredItem> monitoredItems) {
        for (UaMonitoredItem item : monitoredItems) {
            ISourceDataTag dataTag = mapper.getTagBy(item.getClientHandle());
            if (item.getStatusCode().isBad()) {
                notifyEndpointsAboutInvalidTag(dataTag);
            } else {
                mapper.addTagToGroup(dataTag);
            }
        }
    }

    private void notifyEndpointsAboutInvalidTag (ISourceDataTag tag) {
        for (EndpointListener listener : listeners) {
            SourceDataTagQualityCode tagQuality = DataQualityMapper.getBadNodeIdCode();
            listener.onTagInvalid(tag, new SourceDataTagQuality(tagQuality));
        }
    }

    private void notifyEndpointsAboutMonitoredItemChange (final DataValue value, ISourceDataTag tag) {
        for (EndpointListener listener : listeners) {
            SourceDataTagQualityCode tagQuality = DataQualityMapper.getDataTagQualityCode(value.getStatusCode());
            ValueUpdate valueUpdate = new ValueUpdate(value, value.getSourceTime().getUtcTime());
            listener.onNewTagValue(tag, valueUpdate, new SourceDataTagQuality(tagQuality));
        }
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        item.setValueConsumer(value -> {
            ISourceDataTag dataTag = mapper.getTagBy(item.getClientHandle());
            notifyEndpointsAboutMonitoredItemChange(value, dataTag);
        });
    }
}
