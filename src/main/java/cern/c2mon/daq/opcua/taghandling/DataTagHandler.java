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

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.control.Controller;
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
@Component("dataTagHandler")
@Slf4j
@RequiredArgsConstructor
@Lazy
public class DataTagHandler implements IDataTagHandler {

    private final TagSubscriptionManager manager;
    private final MessageSender messageSender;
    private final Controller controller;

    @Override
    public void subscribeTags(@NonNull final Collection<ISourceDataTag> dataTags) {
        synchronized (this) {
            final Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions = dataTags.stream()
                    .collect(groupingBy(ISourceDataTag::getTimeDeadband))
                    .entrySet()
                    .stream()
                    .collect(toMap(e -> manager.getGroup(e.getKey()), e -> e.getValue().stream().map(manager::getOrCreateDefinition).collect(Collectors.toList())));
            final Map<Integer, SourceDataTagQuality> handleQualityMap = controller.subscribe(groupsWithDefinitions);
            handleQualityMap.forEach(this::completeSubscriptionAndReportSuccess);
        }
    }

    @Override
    public boolean subscribeTag(@NonNull final ISourceDataTag sourceDataTag) {
        ItemDefinition definition = manager.getOrCreateDefinition(sourceDataTag);
        final Map<SubscriptionGroup, List<ItemDefinition>> map = new ConcurrentHashMap<>();
        map.put(manager.getGroup(definition.getTimeDeadband()), Collections.singletonList(definition));
        final Map<Integer, SourceDataTagQuality> handleQualityMap = controller.subscribe(map);
        handleQualityMap.forEach(this::completeSubscriptionAndReportSuccess);
        final SourceDataTagQuality quality = handleQualityMap.get(definition.getClientHandle());
        return quality != null && quality.isValid();
    }

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
            } else if (!controller.unsubscribe(definition)) {
                return false;
            }
        }
        manager.removeTag(dataTag.getId());
        return wasSubscribed;
    }

    @Override
    public void refreshAllDataTags() {
        refresh(manager.getTagIdDefinitionMap());
    }

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

    private void completeSubscriptionAndReportSuccess(int clientHandle, SourceDataTagQuality quality) {
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
                final Map.Entry<ValueUpdate, SourceDataTagQuality> reading = controller.read(e.getValue().getNodeId());
                messageSender.onValueUpdate(e.getKey(), reading.getValue(), reading.getKey());
            } catch (OPCUAException ex) {
                log.debug("The DataTag with ID {} could not be refreshed.", e.getKey(), ex);
            }
        }
    }
}