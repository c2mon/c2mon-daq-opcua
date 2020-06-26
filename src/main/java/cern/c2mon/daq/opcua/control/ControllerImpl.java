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

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import com.google.common.base.Joiner;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class ControllerImpl implements Controller {

    private final TagSubscriptionMapper mapper;
    private final EndpointListener endpointListener;
    private final FailoverProxy failoverProxy;

    /** A flag indicating whether the controller was stopped. */
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    /**
     * Attempt to connect to an OPC UA server and notify the {@link cern.c2mon.daq.common.IEquipmentMessageSender} about
     * a failure which prevents the DAQ from starting successfully.
     * @param uri the URI of the OPC UA server to connect to.
     * @throws OPCUAException       of type {@link CommunicationException} if an error not related to authentication
     *                              configuration occurs on connecting to the OPC UA server, and of type {@link
     *                              ConfigurationException} if it is not possible to connect to any of the the OPC UA
     *                              server's endpoints with the given authentication configuration settings.
     */
    @Override
    public void connect(String uri) throws OPCUAException {
        try {
            failoverProxy.initialize(uri);
            stopped.set(false);
        } catch (OPCUAException e) {
            endpointListener.onEquipmentStateUpdate(EndpointListener.EquipmentState.CONNECTION_FAILED);
            throw e;
        }
    }

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    @Override
    public void stop() {
        log.info("Disconnecting... ");
        stopped.set(true);
        mapper.clear();
        final Collection<Endpoint> endpoints = new ArrayList<>(failoverProxy.getPassiveEndpoints());
        endpoints.add(failoverProxy.getActiveEndpoint());
        for (var e : endpoints) {
            try {
                e.disconnect();
            } catch (OPCUAException ex) {
                log.error("Error disconnecting from endpoint with uri {}: ", e.getUri(), ex);
            }
        }
    }

    @Override
    public boolean isStopped() {
        return stopped.get();
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
        final ArrayList<Long> ids = new ArrayList<>();
        for (var e : mapper.mapTagsToGroupsAndDefinitions(dataTags).entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("The thread was interrupted before all tags could be subscribed.");
                return;
            } else if (!subscribeToGroup(e.getKey(), e.getValue())) {
                ids.addAll(e.getValue().stream().map(mapper::getTagId).collect(Collectors.toList()));
            }
        }
        if (!ids.isEmpty()) {
            log.error("Could not subscribe DataTags with IDs {}.", Joiner.on(", ").join(ids));
        }
    }

    /**
     * Subscribes to the OPC UA node corresponding to one data tag on the server.
     * @param sourceDataTag the ISourceDataTag to subscribe to.
     * @return true if the Tag could be subscribed successfully.
     */
    @Override
    public synchronized boolean subscribeTag(@NonNull final ISourceDataTag sourceDataTag) {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        ItemDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);
        return subscribeToGroup(group, Collections.singletonList(definition));
    }

    @Override
    public boolean subscribeToGroup(SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        final Map<UInteger, SourceDataTagQuality> tagQualityMap = subscribeToGroup(failoverProxy.getActiveEndpoint(), group, definitions);
        boolean allSuccessful  = tagQualityMap != null;
        if (allSuccessful) {
            for (var e : tagQualityMap.entrySet()) {
                allSuccessful = allSuccessful && completeSubscriptionAndReportSuccess(e.getKey(), e.getValue());
            }
        }
        failoverProxy.getPassiveEndpoints().forEach(e -> subscribeToGroup(e, group, definitions));
        return allSuccessful;
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
            final UInteger clientHandle = mapper.getDefinition(dataTag.getId()).getClientHandle();
            failoverProxy.getPassiveEndpoints().forEach(e -> deleteAndReport(e, clientHandle, dataTag.getId(), dataTag.getTimeDeadband()));
            if (!deleteAndReport(failoverProxy.getActiveEndpoint(), clientHandle, dataTag.getId(), dataTag.getTimeDeadband())) {
                return false;
            }
        }
        mapper.removeTag(dataTag);
        return wasSubscribed;
    }

    private boolean deleteAndReport(Endpoint endpoint, UInteger clientHandle, long tagID, int publishTime) {
        try {
            endpoint.deleteItemFromSubscription(clientHandle, publishTime);
            return true;
        } catch (OPCUAException ex) {
            log.error("Tag with ID {} could not be completed successfully on endpoint {}.", tagID, endpoint.getUri());
            return false;
        }
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

    private synchronized void refresh(Map<Long, ItemDefinition> entries) {
        final var notRefreshable = new ArrayList<Long>();
        for (var e : entries.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("The thread was interrupted before all tags could be refreshed.");
                return;
            }
            try {
                final var reading = failoverProxy.getActiveEndpoint().read(e.getValue().getNodeId());
                endpointListener.onValueUpdate(e.getValue().getClientHandle(), reading.getValue(), reading.getKey());
            } catch (OPCUAException ex) {
                notRefreshable.add(e.getKey());
            }
        }
        if (!notRefreshable.isEmpty()) {
            log.error("An exception occurred when refreshing ISourceDataTags with IDs {}. ", notRefreshable);
        }
    }

    private Map<UInteger, SourceDataTagQuality> subscribeToGroup(Endpoint endpoint, SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        try {
            return endpoint.subscribeToGroup(group.getPublishInterval(), definitions);
        } catch (OPCUAException ignored) {
            final String ids = Joiner.on(", ").join(definitions.stream().map(mapper::getTagId).toArray());
            log.error("Tags with IDs {} could not be subscribed on endpoint with uri {}. ", ids, endpoint.getUri());
        }
        return null;
    }

    private boolean completeSubscriptionAndReportSuccess(UInteger clientHandle, SourceDataTagQuality quality) {
        if (quality.isValid()) {
            final Long tagId = mapper.getTagId(clientHandle);
            if (tagId != null) {
                mapper.addTagToGroup(tagId);
            }
            return true;
        } else {
            endpointListener.onTagInvalid(clientHandle, quality);
            return false;
        }
    }
}