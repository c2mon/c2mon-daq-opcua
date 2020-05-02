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
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.daq.opcua.connection.ClientWrapper;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.*;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.*;
import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Context.*;


@Slf4j
@Getter
@Component("endpoint")
public class EndpointImpl implements Endpoint {

    @Setter
    @Autowired
    private ClientWrapper wrapper;

    @Autowired
    private final TagSubscriptionMapper mapper;

    @Autowired
    private final EventPublisher publisher;

    private String uri;

    public EndpointImpl(ClientWrapper wrapper, TagSubscriptionMapper mapper, EventPublisher publisher) {
        this.wrapper = wrapper;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    public void initialize(String uri){
        this.uri = uri;
    }

    public void connect(boolean connectionLost){
        if (connectionLost) {
            publisher.notifyEquipmentState(CONNECTION_LOST);
        }
        try {
            wrapper.initialize(uri);
            publisher.notifyEquipmentState(OK);
        } catch (OPCCommunicationException e) {
            publisher.notifyEquipmentState(CONNECTION_FAILED);
            throw e;
        }
    }

    @Override
    public void subscribe (EndpointListener listener) {
        publisher.subscribe(listener);
    }

    @Override
    public void recreateSubscription (UaSubscription subscription) {
        wrapper.deleteSubscription(subscription);
        SubscriptionGroup group = mapper.getGroup(subscription);
        List<DataTagDefinition> definitions = group.getDefinitions();
        group.reset();
        subscribeToGroup(group, definitions);
    }

    @Override
    public synchronized void subscribeTags (@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        if (dataTags.isEmpty()) {
            throw new ConfigurationException(ConfigurationException.Cause.DATATAGS_EMPTY);
        }
        mapper.maptoGroupsWithDefinitions(dataTags).forEach(this::subscribeToGroup);
    }

    @Override
    public synchronized void subscribeTag (@NonNull final ISourceDataTag sourceDataTag) {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        DataTagDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);

        subscribeToGroup(group, Collections.singletonList(definition));
    }

    private void subscribeToGroup (SubscriptionGroup group, List<DataTagDefinition> definitions) {
        UaSubscription subscription = (group.isSubscribed()) ? group.getSubscription()
                : wrapper.createSubscription(group.getPublishInterval());
        group.setSubscription(subscription);

        List<UaMonitoredItem> monitoredItems = wrapper.subscribeItemDefinitions(subscription, definitions, this::itemCreationCallback);
        completeSubscription(monitoredItems);
    }

    @Override
    public synchronized void removeDataTag (final ISourceDataTag dataTag) {
        SubscriptionGroup group = mapper.getGroup(dataTag);

        if (!group.isSubscribed()) {
            throw new IllegalArgumentException("The tag cannot be removed, since the subscription group is not subscribed.");
        }

        UaSubscription subscription = group.getSubscription();
        UInteger clientHandle = mapper.getOrCreateDefinition(dataTag.getId()).getClientHandle();

        wrapper.deleteItemFromSubscription(clientHandle, subscription);
        mapper.removeTagFromGroup(dataTag);
        
        if (group.size() < 1) {
            group.reset();
            wrapper.deleteSubscription(subscription);
        }
    }

    @Override
    public synchronized void refreshDataTags (final Collection<ISourceDataTag> dataTags) {
        for (ISourceDataTag dataTag : dataTags) {
            NodeId address = mapper.getOrCreateDefinition(dataTag).getNodeId();
            final DataValue value = wrapper.read(address);
            notifyPublisherOfEvent(dataTag.getId(), value);
        }
    }

    @Override
    public synchronized void reset () {
        mapper.clear();
        wrapper.disconnect();
    }

    /**
     * Executes the method associated with the tag. If the tag contains both a primary and a reduntand item name,
     * it is assumed that the primary item name refers to the object node containing the methodId, and that the
     * redundant item name refers to the method node.
     * @param tag the command tag associated with a method node.
     * @param arg the input arguments to pass to the method.
     * @return an array of the method's output arguments. If there are none, an empty array is returned.
     */
    @Override
    public Object[] executeMethod(ISourceCommandTag tag, Object arg) {
        log.info("executeMethod of tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        final ItemDefinition def = ItemDefinition.of(tag);
        final Map.Entry<StatusCode, Object[]> response = def.getMethodNodeId() == null ?
                wrapper.callMethod(wrapper.getParentObjectNodeId(def.getNodeId()), def.getNodeId(), arg):
                wrapper.callMethod(def.getNodeId(), def.getMethodNodeId(), arg);
        log.info("executeMethod returned status code {} and output {} .", response.getKey(), response.getValue());
        handleCommandResponseStatusCode(response.getKey(), METHOD);
        return response.getValue();
    }

    @Override
    public void executeCommand(ISourceCommandTag tag, Object arg) {
        log.info("executeCommand on tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        StatusCode write = wrapper.write(ItemDefinition.toNodeId(tag), arg);
        handleCommandResponseStatusCode(write, COMMAND_WRITE);
    }

    @Override
    public void executePulseCommand(ISourceCommandTag tag, Object arg, int pulseLength) {
        log.info("executePulseCommand on tag with ID {} and name {} with argument {} and pulse length {}.", tag.getId(), tag.getName(), arg, pulseLength);
        final NodeId nodeId = ItemDefinition.toNodeId(tag);
        final Object original = MiloMapper.toObject(wrapper.read(nodeId).getValue());
        if (original != null && original.equals(arg)) {
            log.info("{} is already set to {}. Skipping command with pulse.", nodeId, arg);
        } else {
            log.info("Setting {} to {} for {} seconds.", nodeId, arg, pulseLength);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                final StatusCode statusCode = wrapper.write(nodeId, original);
                log.info("Resetting tag with ID {} and name {} to {} returned statusCode {}. ", tag.getId(), tag.getName(), original, statusCode);
                handleCommandResponseStatusCode(statusCode, COMMAND_REWRITE);
            }, pulseLength, TimeUnit.SECONDS);
            scheduler.shutdown();
            final StatusCode statusCode = wrapper.write(nodeId, arg);
            handleCommandResponseStatusCode(statusCode, COMMAND_WRITE);
        }
    }


    @Override
    public synchronized void writeAlive(final OPCHardwareAddress address, final Object value) {
        NodeId nodeId = ItemDefinition.toNodeId(address);
        final StatusCode response = wrapper.write(nodeId, value);
        publisher.notifyAlive(response);
    }

    @Override
    public synchronized boolean isConnected () {
        return wrapper.isConnected();
    }

    private void completeSubscription (List<UaMonitoredItem> monitoredItems) {
        for (UaMonitoredItem item : monitoredItems) {
            Long tagId = mapper.getTagId(item.getClientHandle());
            if (item.getStatusCode().isBad()) {
                publisher.invalidTag(tagId);
            }
            else {
                mapper.addTagToGroup(tagId);
            }
        }
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        Long tag = mapper.getTagId(item.getClientHandle());
        item.setValueConsumer(value -> notifyPublisherOfEvent(tag, value));
    }

    private void handleCommandResponseStatusCode(StatusCode statusCode, OPCCommunicationException.Context context) {
        if (!statusCode.isGood()) {
            throw new OPCCommunicationException(context);
        }
    }

    private void notifyPublisherOfEvent(Long tagId, DataValue value) {
        SourceDataTagQualityCode tagQuality = DataQualityMapper.getDataTagQualityCode(value.getStatusCode());
        ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime());

        publisher.notifyTagEvent(tagId, tagQuality, valueUpdate);
    }
}
