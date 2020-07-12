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
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Controller acts as an interface in between The {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}'s mapping of data
 * points as SourceDataTags, and the {@link cern.c2mon.daq.opcua.connection.Endpoint}'s mapping of points in the address space
 * using uint clientHandles.
 */
@Component("controller")
@Slf4j
@RequiredArgsConstructor
@Lazy
public class TagControllerImpl implements TagController {

    private final TagSubscriptionMapper mapper;
    private final MessageSender messageSender;
    private final FailoverProxy failoverProxy;

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
        final Collection<ItemDefinition> definitions = dataTags.stream().map(mapper::getOrCreateDefinition).collect(Collectors.toList());
        final Map<UInteger, SourceDataTagQuality> handleQualityMap = failoverProxy.subscribeDefinitions(definitions);
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
        final Map<UInteger, SourceDataTagQuality> handleQualityMap = failoverProxy.subscribeDefinitions(Collections.singletonList(definition));
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
        final boolean wasSubscribed = mapper.wasSubscribed(dataTag);
        if (wasSubscribed) {
            log.info("Unsubscribing tag from server.");
            try {
                failoverProxy.unsubscribeDefinition(mapper.getDefinition(dataTag.getId()));
            } catch (OPCUAException e) {
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
                final var reading = failoverProxy.getActiveEndpoint().read(e.getValue().getNodeId());
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