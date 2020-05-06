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

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.daq.config.ChangeReport;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.*;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Component("controller")
@Slf4j
public class ControllerImpl implements Controller, IDataTagChanger {

    @Setter
    @Autowired
    private Endpoint endpoint;

    @Autowired
    private final TagSubscriptionMapper mapper;

    @Autowired
    private final EndpointListener listener;

    private String uri;

    public ControllerImpl(Endpoint endpoint, TagSubscriptionMapper mapper, EndpointListener listener) {
        this.endpoint = endpoint;
        this.mapper = mapper;
        this.listener = listener;
    }

    @Override
    public synchronized void initialize(String uri, Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        this.uri = uri;
        connect();
        subscribeTags(dataTags);
    }


    @Override
    public synchronized void subscribeTags (@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        if (dataTags.isEmpty()) {
            throw new ConfigurationException(ConfigurationException.Cause.DATATAGS_EMPTY);
        }
        mapper.mapTagsToGroupsAndDefinitions(dataTags).forEach(this::subscribeToGroup);
    }

    @Override
    public synchronized void stop () {
        mapper.clear();
        endpoint.disconnect();
    }

    @Override
    public synchronized void refreshAllDataTags () {
        mapper.getTagIdDefinitionMap().forEach((tagId, definition) -> refresh(tagId, definition.getNodeId()));
    }

    @Override
    public synchronized void refreshDataTag (ISourceDataTag sourceDataTag) {
        refresh(sourceDataTag.getId(), mapper.getOrCreateDefinition(sourceDataTag).getNodeId());
    }

    @Override
    public synchronized void writeAlive(final OPCHardwareAddress address, final Object value) {
        NodeId nodeId = ItemDefinition.toNodeId(address);
        final StatusCode response = endpoint.write(nodeId, value);
        listener.onAlive(response);
    }

    @Override
    public synchronized boolean isConnected () {
        return endpoint.isConnected();
    }

    @Override
    public void recreateSubscription (UaSubscription subscription) {
        endpoint.deleteSubscription(subscription);
        SubscriptionGroup group = mapper.getGroup(subscription);
        final List<Long> tagIds = group.getTagIds();
        group.reset();
        final List<DataTagDefinition> definitions = tagIds.stream().map(mapper::getDefinition).collect(Collectors.toList());
        subscribeToGroup(group, definitions);
    }

    @Override
    public void onAddDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        doAndReport(changeReport,
                () -> {
                    String s = subscribeTag(sourceDataTag);
                    refreshDataTag(sourceDataTag);
                    return s;
                });
    }

    @Override
    public void onRemoveDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        doAndReport(changeReport, () -> removeTag(sourceDataTag));
    }

    @Override
    public void onUpdateDataTag (final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            completeSuccessFullyWithMessage(changeReport, "The new and old sourceDataTags have the same hardware address, no update was required.");
        } else {
            doAndReport(changeReport, () -> {
                String s = removeTag(oldSourceDataTag) + subscribeTag(sourceDataTag);
                refreshDataTag(sourceDataTag);
                return s;
            });
        }
    }

    private synchronized String subscribeTag (@NonNull final ISourceDataTag sourceDataTag) {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        DataTagDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);

        if (subscribeToGroup(group, Collections.singletonList(definition))) {
            return "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId() + " was subscribed. ";
        } else {
            throw new RuntimeException("Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId() + " could not be subscribed. ");
        }
    }

    private synchronized String removeTag(final ISourceDataTag dataTag) {
        String report = "";
        SubscriptionGroup group = mapper.getGroup(dataTag);
        if (group.isSubscribed() && group.contains(dataTag)) {
            log.info("Unsubscribing tag from server.");
            UaSubscription subscription = group.getSubscription();
            UInteger clientHandle = mapper.getDefinition(dataTag.getId()).getClientHandle();
            endpoint.deleteItemFromSubscription(clientHandle, subscription);
            report += "Tag " + dataTag.getName() + " with ID " + dataTag.getId() + " was unsubscribed";
            if (group.size() < 1) {
                log.info("Subscription is empty. Deleting subscription.");
                endpoint.deleteSubscription(subscription);
            }
        } else {
            log.info("The tag was not subscribed.");
        }
        if (mapper.removeTag(dataTag)) {
            report += report.trim().length() > 0 ? ". " : "and removed from configuration. ";
            log.info("Removing tag from configuration.");
        } else {
            report += "Tag " + dataTag.getName() + " with ID " + dataTag.getId() + " was not subscribed or configured. No change required. ";
        }
        if (group.size() < 1) {
            group.reset();
        }
        return report;
    }

    private void doAndReport (ChangeReport changeReport, Supplier<String> supplier) {
        try {
            reconnectIfRequired();
            completeSuccessFullyWithMessage(changeReport, supplier.get());
        } catch (Exception e) {
            changeReport.appendError(e.getMessage());
            changeReport.setState(FAIL);
        }
    }

    private void reconnectIfRequired() {
        if (!isConnected()) {
            log.info("Connection lost. Reconnecting.");
            reconnect();
            refreshAllDataTags();
        }
    }

    private void completeSuccessFullyWithMessage (ChangeReport changeReport, String message) {
        changeReport.appendInfo(message);
        changeReport.setState(SUCCESS);
    }

    private void connect(){
        try {
            endpoint.initialize(uri);
            listener.onEquipmentStateUpdate(OK);
        } catch (OPCCommunicationException e) {
            listener.onEquipmentStateUpdate(CONNECTION_FAILED);
            throw e;
        }
    }

    private void reconnect() {
        listener.onEquipmentStateUpdate(CONNECTION_LOST);
        final Map<Long, DataTagDefinition> currentConfig = new ConcurrentHashMap<>(mapper.getTagIdDefinitionMap());
        mapper.clear();
        if (endpoint.isConnected()) {
            endpoint.disconnect();
        }
        connect();
        mapper.addToTagDefinitionMap(currentConfig);
        mapper.mapToGroups(currentConfig.values()).forEach(this::subscribeToGroup);
    }

    private void refresh(long tagId, NodeId address) {
        final DataValue value = endpoint.read(address);
        notifyListener(tagId, value);
    }

    private boolean subscribeToGroup (SubscriptionGroup group, List<DataTagDefinition> definitions) {
        if (!group.isSubscribed()) {
            final var subscription = endpoint.createSubscription(group.getPublishInterval());
            group.setSubscription(subscription);
        }
        final var monitoredItems = endpoint.subscribeItemDefinitions(group.getSubscription(), definitions, this::itemCreationCallback);
        return completeSubscription(monitoredItems);
    }

    private boolean completeSubscription (List<UaMonitoredItem> monitoredItems) {
        boolean allSuccessful = true;
        for (UaMonitoredItem item : monitoredItems) {
            Long tagId = mapper.getTagId(item.getClientHandle());
            final var statusCode = item.getStatusCode();
            if (statusCode.isBad()) {
                allSuccessful = false;
                final var qualityCode = DataQualityMapper.getDataTagQualityCode(statusCode);
                listener.onTagInvalid(tagId, new SourceDataTagQuality(qualityCode));
            } else {
                mapper.addTagToGroup(tagId);
            }
        }
        return allSuccessful;
    }

    private void itemCreationCallback (UaMonitoredItem item, Integer i) {
        Long tag = mapper.getTagId(item.getClientHandle());
        item.setValueConsumer(value -> notifyListener(tag, value));
    }

    private void notifyListener(Long tagId, DataValue value) {
        SourceDataTagQualityCode tagQuality = DataQualityMapper.getDataTagQualityCode(value.getStatusCode());
        ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime());

        if (!tagQuality.equals(SourceDataTagQualityCode.OK)) {
            listener.onTagInvalid(tagId, new SourceDataTagQuality(tagQuality));
        } else {
            listener.onNewTagValue(tagId, valueUpdate, new SourceDataTagQuality(tagQuality));
        }
    }
}
