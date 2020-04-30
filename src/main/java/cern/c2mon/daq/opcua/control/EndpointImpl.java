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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
        DataTagDefinition definition = mapper.getDefinition(sourceDataTag);

        subscribeToGroup(group, Collections.singletonList(definition));
    }

    private void subscribeToGroup (SubscriptionGroup group, List<DataTagDefinition> definitions) {
        UaSubscription subscription = (group.isSubscribed()) ? group.getSubscription()
                : wrapper.createSubscription(group.getDeadband().getTime());
        group.setSubscription(subscription);

        List<UaMonitoredItem> monitoredItems = wrapper.subscribeItemDefinitions(subscription, definitions, group.getDeadband(), this::itemCreationCallback);
        completeSubscription(monitoredItems);
    }

    @Override
    public synchronized void removeDataTag (final ISourceDataTag dataTag) {
        SubscriptionGroup group = mapper.getGroup(dataTag);

        if (!group.isSubscribed()) {
            throw new IllegalArgumentException("The tag cannot be removed, since the subscription group is not subscribed.");
        }

        UaSubscription subscription = group.getSubscription();
        UInteger clientHandle = mapper.getDefinition(dataTag).getClientHandle();

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
            NodeId address = mapper.getDefinition(dataTag).getNodeId();
            final DataValue value = wrapper.read(address);
            notifyPublisherOfEvent(dataTag, value);
        }
    }

    @Override
    public synchronized void reset () {
        mapper.clear();
        wrapper.disconnect();
    }

    @Override
    public Object[] executeMethod(ISourceCommandTag tag, Object arg) {
        log.info("Executing method of tag {} with argument {}.", tag, arg);
        final Map.Entry<StatusCode, Object[]> response = wrapper.callMethod(mapper.getDefinition(tag), arg);
        log.info("Executing commandTag returned status code {} and output {} .", response.getKey(), response.getValue());
        handleStatusCode(response.getKey(), METHOD);
        return response.getValue();
    }

    @Override
    public void executeCommand(ISourceCommandTag tag, Object arg) {
        log.info("Writing {} to {}.", tag, arg);
        StatusCode write = wrapper.write(mapper.getDefinition(tag).getNodeId(), arg);
        handleStatusCode(write, COMMAND_WRITE);
    }

    @Override
    public void executePulseCommand(ISourceCommandTag tag, Object arg, int pulseLength) {
        final NodeId nodeId = mapper.getDefinition(tag).getNodeId();
        final Object original = MiloMapper.toObject(wrapper.read(nodeId).getValue());
        if (original != null && original.equals(arg)) {
            log.info("{} is already set to {}. Skipping command with pulse.", nodeId, arg);
        } else {
            log.info("Setting {} to {} for {} seconds.", nodeId, arg, pulseLength);
            Executor delayed = CompletableFuture.delayedExecutor(pulseLength, TimeUnit.SECONDS);
            CompletableFuture.runAsync(() -> {
                final StatusCode statusCode = wrapper.write(nodeId, original);
                log.info("Resetting {} to {} returned statusCode {}. ", tag, original, statusCode);
                handleStatusCode(statusCode, COMMAND_REWRITE);
            }, delayed);
            final StatusCode statusCode = wrapper.write(nodeId, arg);
            handleStatusCode(statusCode, COMMAND_WRITE);
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
            ISourceDataTag dataTag = mapper.getTag(item.getClientHandle());
            if (item.getStatusCode().isBad()) {
                publisher.invalidTag(dataTag);
            }
            else {
                mapper.addTagToGroup(dataTag);
            }
        }
    }

    private void handleStatusCode(StatusCode statusCode, OPCCommunicationException.Context context) {
        if (!statusCode.isGood()) {
            throw new OPCCommunicationException(context);
        }
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        ISourceDataTag tag = mapper.getTag(item.getClientHandle());
        item.setValueConsumer(value -> notifyPublisherOfEvent(tag, value));
    }


    private void notifyPublisherOfEvent(ISourceDataTag dataTag, DataValue value) {
        SourceDataTagQualityCode tagQuality = DataQualityMapper.getDataTagQualityCode(value.getStatusCode());
        ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime());

        publisher.notifyTagEvent(dataTag, tagQuality, valueUpdate);
    }
}
