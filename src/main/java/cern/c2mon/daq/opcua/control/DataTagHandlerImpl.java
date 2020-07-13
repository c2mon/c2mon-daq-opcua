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

import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ControllerProxy;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * The Controller acts as an interface in between The {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}'s mapping of data
 * points as SourceDataTags, and the {@link cern.c2mon.daq.opcua.connection.Endpoint}'s mapping of points in the address space
 * using uint clientHandles.
 */
@Component("tagController")
@Slf4j
@RequiredArgsConstructor
@Lazy
public class DataTagHandlerImpl implements DataTagHandler {

    private final TagSubscriptionManager mapper;
    private final MessageSender messageSender;
    private final ControllerProxy controllerProxy;

    @Override
    public void reset() {
        mapper.clear();
    }

    /**
     * Subscribes to the OPC UA nodes corresponding to the data tags on the server.
     * @param dataTags the collection of ISourceDataTags to subscribe to.
     * @throws ConfigurationException if the collection of tags was empty.
     */
    @Override
    public synchronized void subscribeTags(@NonNull final Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        if (dataTags.isEmpty()) {
            throw new ConfigurationException(ExceptionContext.DATATAGS_EMPTY);
        }
        final Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions = dataTags.stream()
                .map(mapper::getOrCreateDefinition)
                .collect(groupingBy(ItemDefinition::getTimeDeadband))
                .entrySet()
                .stream()
                .collect(toMap(e -> mapper.getGroup(e.getKey()), Map.Entry::getValue));
        final Map<UInteger, SourceDataTagQuality> handleQualityMap = controllerProxy.getController().subscribe(groupsWithDefinitions);
        handleQualityMap.forEach(this::completeSubscriptionAndReportSuccess);
    }


    /**
     * Subscribes to the OPC UA node corresponding to one data tag on the server.
     * @param sourceDataTag the ISourceDataTag to subscribe to.
     * @return true if the Tag could be subscribed successfully.
     */
    @Override
    public synchronized boolean subscribeTag(@NonNull final ISourceDataTag sourceDataTag) {
        ItemDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);
        final ConcurrentHashMap<SubscriptionGroup, List<ItemDefinition>> map = new ConcurrentHashMap<>();
        map.put(mapper.getGroup(definition.getTimeDeadband()), Collections.singletonList(definition));
        final Map<UInteger, SourceDataTagQuality> handleQualityMap = controllerProxy.getController().subscribe(map);
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
    public synchronized boolean removeTag(final ISourceDataTag dataTag) {
        final SubscriptionGroup group = mapper.getGroup(dataTag.getTimeDeadband());
        final boolean wasSubscribed = group != null && group.contains(dataTag);
        if (wasSubscribed) {
            log.info("Unsubscribing tag from server.");
            if (!controllerProxy.getController().unsubscribe(mapper.getDefinition(dataTag.getId()))) {
                return false;
            }
        }
        mapper.removeTag(dataTag);
        return wasSubscribed;
    }

    /**
     * Reads the current values from the server for all subscribed data tags.
     */
    @Override
    public synchronized void refreshAllDataTags() {
        refresh(mapper.getTagIdDefinitionMap());
    }

    /**
     * Reads the sourceDataTag's current value from the server
     * @param sourceDataTag the tag whose current value to read
     */
    @Override
    public synchronized void refreshDataTag(ISourceDataTag sourceDataTag) {
        refresh(Map.of(sourceDataTag.getId(), mapper.getOrCreateDefinition(sourceDataTag)));
    }

    private void completeSubscriptionAndReportSuccess(UInteger clientHandle, SourceDataTagQuality quality) {
        if (quality.isValid()) {
            final Long tagId = mapper.getTagId(clientHandle);
            if (tagId != null) {
                mapper.addTagToGroup(tagId);
            }
        } else {
            messageSender.onTagInvalid(clientHandle, quality);
        }
    }

    private synchronized void refresh(Map<Long, ItemDefinition> entries) {
        final var notRefreshable = new ArrayList<Long>();
        for (var e : entries.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("The thread was interrupted before all tags could be refreshed.");
                return;
            }
            try {
                final var reading = controllerProxy.getController().read(e.getValue().getNodeId());
                messageSender.onValueUpdate(e.getValue().getClientHandle(), reading.getValue(), reading.getKey());
            } catch (OPCUAException ex) {
                notRefreshable.add(e.getKey());
            }
        }
        if (!notRefreshable.isEmpty()) {
            log.error("An exception occurred when refreshing ISourceDataTags with IDs {}. ", notRefreshable);
        }
    }


}