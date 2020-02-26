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

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.GroupDefinitionPair;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Wraps interaction with the Milo Client.
 */
@Slf4j
public class EndpointImpl implements Endpoint {

    private static Executor executor = Executors.newCachedThreadPool();
    private final Collection<EndpointListener> listeners = new ConcurrentLinkedQueue<>();
    private OpcUaClient client;
    private TagSubscriptionMapper tagSubscriptionMapper;

    @Getter
    private EquipmentAddress equipmentAddress;

    public EndpointImpl(TagSubscriptionMapper tagSubscriptionMapper) {
        this(tagSubscriptionMapper, null);
    }

    public EndpointImpl(TagSubscriptionMapper tagSubscriptionMapper, final OpcUaClient client) {
        this.tagSubscriptionMapper = tagSubscriptionMapper;
        this.client = client;
    }

    public synchronized void initialize(EquipmentAddress address) {
        this.equipmentAddress = address;
        String uri = this.equipmentAddress.getUriString();
        SecurityPolicy sp = SecurityPolicy.None;

        try {
            this.client = createClient(uri, sp)
                    .thenCompose(OpcUaClient::connect)
                    .thenApply(OpcUaClient.class::cast)
                    .get();
        } catch (Exception e) {
            this.client = null;
            throw new OPCCommunicationException("Could not connect to the OPC Server.", e);
        }
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTags(@NonNull final Collection<ISourceDataTag> dataTags) {
        if (dataTags.isEmpty()) {
            throw new IllegalArgumentException("No data tags to subscribe.");
        }

        return CompletableFuture.allOf(tagSubscriptionMapper
                .toGroups(dataTags)
                .entrySet()
                .stream()
                .map(e -> subscribeToGroup(e.getKey(), e.getValue()))
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public synchronized CompletableFuture<Void> subscribeTag(@NonNull final ISourceDataTag sourceDataTag) {
        GroupDefinitionPair pair = tagSubscriptionMapper.toGroup(sourceDataTag);
        return CompletableFuture.allOf(subscribeToGroup(pair.getSubscriptionGroup(), Collections.singletonList(pair.getItemDefinition())));
    }

    private CompletableFuture<List<UaMonitoredItem>> subscribeToGroup(SubscriptionGroup group, List<ItemDefinition> definitions){
            UaSubscription subscription = (group.isSubscribed()) ? group.getSubscription() : createSubscription(group);
            group.setSubscription(subscription);
            return subscribeItemDefinitions(subscription, definitions, group.getDeadband());
    }

    private UaSubscription createSubscription(SubscriptionGroup group) {
        OpcUaSubscriptionManager subscriptionManager = client.getSubscriptionManager();
        try {
            return subscriptionManager.createSubscription(group.getDeadband().getTime()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException("Could not create a subscription", e);
        }
    }

    private CompletableFuture<List<UaMonitoredItem>> subscribeItemDefinitions(UaSubscription subscription, Collection<ItemDefinition> definitions, Deadband deadband) {
        List<MonitoredItemCreateRequest> requests = definitions
                .stream()
                .map(definition -> createItemSubscriptionRequest(definition, deadband))
                .collect(Collectors.toList());
        return subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback(definitions));
    }

    private BiConsumer<UaMonitoredItem, Integer> itemCreationCallback(Collection<ItemDefinition> definitions) {
        tagSubscriptionMapper.registerDefinitionsInGroup(definitions);
        return (item, i) -> item.setValueConsumer(value -> notifyEndpointsAboutMonitoredItemChange(value, item.getClientHandle()));
    }

    private MonitoredItemCreateRequest createItemSubscriptionRequest(ItemDefinition definition, Deadband deadband) {
        // What is a sensible value for the queue size? Must be large enough to hold all notifications queued in between publishing cycles.
        // Currently, we are only keeping the newest value.
        int queueSize = 0;
        DataChangeFilter filter = new DataChangeFilter(DataChangeTrigger.StatusValue, uint(deadband.getType()), (double) deadband.getValue());

        MonitoringParameters parameters = new MonitoringParameters(definition.getClientHandle(),
                (double) deadband.getTime(),
                ExtensionObject.encode(client.getSerializationContext(), filter),
                uint(queueSize),
                true);

        ReadValueId readValueId = new ReadValueId(definition.getAddress(), AttributeId.Value.uid(),null,QualifiedName.NULL_VALUE);
        return new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters);
    }

    @Override
    public synchronized CompletableFuture<Void> removeDataTag(final ISourceDataTag dataTag) {
        GroupDefinitionPair pair = tagSubscriptionMapper.removeTagFromGroup(dataTag);
        SubscriptionGroup subscriptionGroup = pair.getSubscriptionGroup();

        if (!subscriptionGroup.isSubscribed()) {
            throw new IllegalArgumentException("The tag cannot be removed, since the subscription group is not subscribed.");
        }

        return removeItemFromSubscription(pair, subscriptionGroup);
    }

    private CompletableFuture<Void> removeItemFromSubscription(GroupDefinitionPair pair, SubscriptionGroup subscriptionGroup) {
        UaSubscription subscription = subscriptionGroup.getSubscription();
        return subscriptionGroup.isEmpty() ?
                removeSubscription(subscriptionGroup, subscription) :
                removeItemFromSubscription(pair.getItemDefinition(), subscription);
    }


    private CompletableFuture<Void> removeItemFromSubscription(ItemDefinition definitionToRemove, UaSubscription subscription) {
        List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems()
                .stream()
                .filter(uaMonitoredItem -> definitionToRemove.getClientHandle().equals(uaMonitoredItem.getClientHandle()))
                .collect(Collectors.toList());
        return CompletableFuture.allOf(subscription.deleteMonitoredItems(itemsToRemove));
    }

    private CompletableFuture<Void> removeSubscription(SubscriptionGroup subscriptionGroup, UaSubscription subscription) {
        subscriptionGroup.setSubscription(null);
        return CompletableFuture.allOf(client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
    }

    /**
     * Refreshes the values for the provided data tags.
     *
     * @param dataTags The data tags whose values shall be refreshed.
     */
    @Override
    public synchronized void refreshDataTags(
            final Collection<ISourceDataTag> dataTags) {
        //TODO
    }

    /**
     * Registers an endpoint listener.
     *
     * @param endpointListener The listener to add.
     */
    @Override
    public void registerEndpointListener(
            final EndpointListener endpointListener) {
        listeners.add(endpointListener);
    }

    /**
     * Unregisters an endpoint listener.
     *
     * @param endpointListener The endpoint listener to remove.
     */
    @Override
    public void unRegisterEndpointListener(
            final EndpointListener endpointListener) {
        listeners.remove(endpointListener);
    }

    private CompletableFuture<OpcUaClient> createClient(String uri, SecurityPolicy sp) {
        return DiscoveryClient
                .getEndpoints(uri)
                .thenCompose(endpoints -> {
                        // TODO Authentication
                        EndpointDescription endpoint = findEndpointMatchingSecurityPolicy(endpoints, sp);
                        OpcUaClientConfig config = OpcUaClientConfig.builder().setEndpoint(endpoint).build();
                    try {
                        return CompletableFuture.completedFuture(OpcUaClient.create(config));
                    } catch (UaException e) {
                        CompletableFuture<OpcUaClient> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(e);
                        return failedFuture;
                    }
                });
    }

    private EndpointDescription findEndpointMatchingSecurityPolicy(List<EndpointDescription> endpoints, SecurityPolicy sp) {
        return endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(sp.getUri()))
                .findFirst()
                .orElseThrow(() -> new OPCCommunicationException("The server does not offer any endpoints matching the configuration."));
    }

    /**
     * Stops and resets the endpoint completely.
     */
    @Override
    public synchronized void reset() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        listeners.clear();
        tagSubscriptionMapper.clear();
    }

    /**
     * Writes a value to an item and rewrites it after a provided pulse length.
     *
     * @param itemDefintion The item definition which defines where to write.
     * @param pulseLength   The pulse length after which the value should be
     *                      rewritten.
     * @param value         The value to write.
     */
    private void writeRewrite(final ItemDefinition itemDefintion,
                              final int pulseLength, final Object value) {
        onWrite(itemDefintion, value);

        try {
            Thread.sleep(pulseLength);
        } catch (InterruptedException e) {
            throw new OPCCriticalException("Sleep Interrupted.");
        } finally {
            if (value instanceof Boolean) {
                onWrite(itemDefintion, !((Boolean) value));
            } else if (value instanceof String) {
                onWrite(itemDefintion, "");
            } else {
                // This applies to all numeric use cases and Bytes
                onWrite(itemDefintion, value.getClass().cast(0));
            }
        }
    }

    /**
     * Writes a value to the OPC server. Used only be the AliveWriter
     *
     * @param address The address which defines where to write.
     * @param value   The value to write.
     */
    @Override
    public synchronized void write(
            final OPCHardwareAddress address, final Object value) {
//        ItemDefinition itemDefinition = ItemDefinitionFactory.createItemDefinition(1L, address);
//        if (itemDefinition != null) {
//            onWrite(itemDefinition, value);
//        }
    }

    @Override
    public synchronized boolean isConnected() {
        try {
            client.getSession().get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Writes to an item defined by the item definition.
     *
     * @param itemDefinition The item definition which defines where to write.
     * @param value          The value to write.
     */
    protected void onWrite(ItemDefinition itemDefinition, Object value) {
        NodeId nodeId = itemDefinition.getAddress();
        Variant v = new Variant(value);
        try {
            StatusCode statusCode = client.writeValue(nodeId, new DataValue(v)).get();
            if (!statusCode.isGood()) {
                throw new OPCCommunicationException("Write failed");
            }
        } catch (Exception e) {
            throw new OPCCommunicationException(e);
        }
    }


    /**
     * Executes the command for the provided item definition.
     *
     * @param itemDefinition The item definition for the command.
     * @param value         The values used as parameters for the command.
     */
    protected void onCallMethod(ItemDefinition itemDefinition, Object... value) {

    }

    private void notifyEndpointsAboutMonitoredItemChange(final DataValue value, UInteger clientHandle) {
        ISourceDataTag dataTag = tagSubscriptionMapper.getTagBy(clientHandle);

        executor.execute(() -> {
            for (EndpointListener listener: listeners) {
                if (value.getStatusCode() == StatusCode.GOOD) {
                    listener.onNewTagValue(dataTag, value.getSourceTime().getUtcTime(), value.getValue().getValue());
                } else {
                    listener.onTagInvalidException(dataTag,  new OPCCommunicationException(value.getStatusCode().toString()));
                }

            }

        });
    }
}
