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

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.*;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cern.c2mon.daq.opcua.upstream.EndpointListener.EquipmentState.*;


@Slf4j
@Getter
@Component("endpoint")
public class EndpointImpl implements Endpoint {

    @Setter
    @Autowired
    private MiloClientWrapper wrapper;

    @Autowired
    private final TagSubscriptionMapper mapper;

    @Autowired
    private final EventPublisher publisher;

    private String uri;

    public EndpointImpl(MiloClientWrapper wrapper, TagSubscriptionMapper mapper, EventPublisher publisher) {
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
            NodeId address = mapper.getDefinition(dataTag).getAddress();
            wrapper.read(address).forEach(value -> publisher.notifyTagEvent(value.getStatusCode(), dataTag, value));
        }
    }

    @Override
    public synchronized void reset () {
        mapper.clear();
        wrapper.disconnect();
    }

    @Override
    public void executeCommand(ISourceCommandTag tag, SourceCommandTagValue command) throws ConfigurationException {
        CommandTagDefinition def = mapper.getDefinition(tag);
        Object value = TypeConverter.cast(command.getValue(), command.getDataType());
        if (value == null) {
            throw new ConfigurationException(ConfigurationException.Cause.COMMAND_VALUE_ERROR);
        }

        OPCHardwareAddress hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
        switch (hardwareAddress.getCommandType()) {
            case METHOD:
                log.debug("Not yet implemented");
                break;
            case CLASSIC:
                int pulseLength = hardwareAddress.getCommandPulseLength();
                if (pulseLength > 0) {
                    log.debug("Not yet implemented");
                } else {
                    StatusCode write = wrapper.write(def.getAddress(), value);
                    publisher.notifyWriteEvent(write, tag);
                }
                break;
            default:
                throw new ConfigurationException(ConfigurationException.Cause.COMMAND_TYPE_UNKNOWN);
        }
    }

    @Override
    public synchronized StatusCode write (final OPCHardwareAddress address, final Object value) {
        NodeId nodeId = ItemDefinition.toNodeId(address);
        return wrapper.write(nodeId, value);
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

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        ISourceDataTag tag = mapper.getTag(item.getClientHandle());
        item.setValueConsumer(value -> publisher.notifyTagEvent(item.getStatusCode(), tag, value));
    }
}
