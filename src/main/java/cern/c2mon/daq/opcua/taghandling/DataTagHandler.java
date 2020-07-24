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

package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.control.IControllerProxy;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * The {@link DataTagHandler} is responsible for managing the state of subscribed {@link ISourceDataTag}s in {@link
 * TagSubscriptionMapper}, and triggers the subscription to or removal of subscriptions of {@link ISourceDataTag}s from
 * the server.
 */
@Component("tagController")
@Slf4j
@RequiredArgsConstructor
@Lazy
public class DataTagHandler implements IDataTagHandler {

    private final TagSubscriptionManager manager;
    private final IMessageSender messageSender;
    private final IControllerProxy controllerProxy;

    /**
     * Subscribes to the OPC UA nodes corresponding to the data tags on the server.
     * @param dataTags the collection of ISourceDataTags to subscribe to.
     * @throws ConfigurationException if the collection of tags was empty.
     */
    @Override
    public void subscribeTags(@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        if (dataTags.isEmpty()) {
            throw new ConfigurationException(ExceptionContext.DATATAGS_EMPTY);
        }
        synchronized (this) {
            final Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions = dataTags.stream()
                    .collect(groupingBy(ISourceDataTag::getTimeDeadband))
                    .entrySet()
                    .stream()
                    .collect(toMap(e -> manager.getGroup(e.getKey()), e -> e.getValue().stream().map(manager::getOrCreateDefinition).collect(Collectors.toList())));
            final Map<UInteger, SourceDataTagQuality> handleQualityMap = controllerProxy.subscribe(groupsWithDefinitions);
            handleQualityMap.forEach(this::completeSubscriptionAndReportSuccess);
        }
    }

    /**
     * Subscribes to the OPC UA node corresponding to one data tag on the server.
     * @param sourceDataTag the ISourceDataTag to subscribe to.
     * @return true if the Tag could be subscribed successfully.
     */
    @Override
    public boolean subscribeTag(@NonNull final ISourceDataTag sourceDataTag) {
        ItemDefinition definition = manager.getOrCreateDefinition(sourceDataTag);
        final Map<SubscriptionGroup, List<ItemDefinition>> map = new ConcurrentHashMap<>();
        map.put(manager.getGroup(definition.getTimeDeadband()), Collections.singletonList(definition));
        final Map<UInteger, SourceDataTagQuality> handleQualityMap = controllerProxy.subscribe(map);
        handleQualityMap.forEach(this::completeSubscriptionAndReportSuccess);
        final SourceDataTagQuality quality = handleQualityMap.get(definition.getClientHandle());
        return quality != null && quality.isValid();
    }

    /**
     * Removes a Tag from the internal configuration and if already subscribed from the OPC UA subscription. It the
     * subscription is then empty, it is deleted along with the monitored item.
     * @param dataTag the tag to remove.
     * @return true if the tag was previously known.
     */
    @Override
    public boolean removeTag(final ISourceDataTag dataTag) {
        final SubscriptionGroup group = manager.getGroup(dataTag.getTimeDeadband());
        final boolean wasSubscribed = group != null && group.contains(dataTag.getId());
        if (wasSubscribed) {
            log.info("Unsubscribing tag from server.");
            final ItemDefinition definition = manager.getDefinition(dataTag.getId());
            if (definition == null) {
                log.error("The tag with id {} is not currently subscribed on the server, skipping removal.", dataTag.getId());
                return false;
            } else if (!controllerProxy.unsubscribe(definition)) {
                return false;
            }
        }
        manager.removeTag(dataTag.getId());
        return wasSubscribed;
    }

    /**
     * Reads the current values from the server for all subscribed data tags.
     */
    @Override
    public void refreshAllDataTags() {
        refresh(manager.getTagIdDefinitionMap());
    }

    /**
     * Reads the sourceDataTag's current value from the server
     * @param sourceDataTag the tag whose current value to read
     */
    @Override
    public void refreshDataTag(ISourceDataTag sourceDataTag) {
        final Map<Long, ItemDefinition> objectObjectHashMap = new ConcurrentHashMap<>();
        objectObjectHashMap.put(sourceDataTag.getId(), manager.getOrCreateDefinition(sourceDataTag));
        refresh(objectObjectHashMap);
    }

    /**
     * Resets the state of the the {@link IDataTagHandler} and of the subscribed {@link ISourceDataTag}s in {@link
     * TagSubscriptionMapper}.
     */
    @Override
    public void reset() {
        manager.clear();
    }

    private void completeSubscriptionAndReportSuccess(UInteger clientHandle, SourceDataTagQuality quality) {
        final Long tagId = manager.getTagId(clientHandle);
        if (quality.isValid() && tagId != null) {
            manager.addTagToGroup(tagId);
        } else if (tagId != null) {
            messageSender.onTagInvalid(tagId, quality);
        } else {
            log.error("Inconsistent state, Cannot associate the client handle with a DataTag ID.");
        }
    }

    private void refresh(Map<Long, ItemDefinition> entries) {
        for (Map.Entry<Long, ItemDefinition> e : entries.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("The thread was interrupted before all tags could be refreshed.");
                return;
            }
            try {
                final Map.Entry<ValueUpdate, SourceDataTagQuality> reading = controllerProxy.read(e.getValue().getNodeId());
                messageSender.onValueUpdate(e.getKey(), reading.getValue(), reading.getKey());
            } catch (OPCUAException ex) {
                log.debug("The DataTag with ID {} could not be refreshed.", e.getKey(), ex);
            }
        }
    }
}