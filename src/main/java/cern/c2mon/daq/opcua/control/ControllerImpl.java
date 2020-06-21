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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
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
        stopped.set(true);
        mapper.clear();
        try {
            failoverProxy.disconnect();
        } catch (OPCUAException e) {
            log.error("Error disconnecting: ", e);
        }
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
                final UInteger clientHandle = mapper.getDefinition(dataTag.getId()).getClientHandle();
                failoverProxy.getEndpoint().deleteItemFromSubscription(clientHandle, dataTag.getTimeDeadband());
            } catch (OPCUAException e) {
                log.error("Deleting empty subscription could not be completed successfully.");
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

    /**
     * Called when a subscription could not be automatically transferred in between sessions. In this case, the
     * subscription is recreated from scratch. If the controller received the command to stop, the subscription is not
     * recreated and the method exists silently.
     * @param publishInterval the publishInterval of the old session which must be recreated.
     * @throws OPCUAException of type {@link CommunicationException} if the subscription could not be recreated due to
     *                        communication difficulty, and of type {@link ConfigurationException} if the subscription
     *                        can not be mapped to current DataTags, and therefore cannot be recreated.
     */
    @Override
    @Retryable(value = CommunicationException.class,
            maxAttempts = Integer.MAX_VALUE,
            backoff = @Backoff(delayExpression = "${app.retryDelay}"))
    public synchronized void recreateSubscription(Endpoint endpoint, int publishInterval) throws OPCUAException {
        if (stopped.get() || Thread.currentThread().isInterrupted()) {
            log.info("Controller was stopped, cease subscription recreation attempts.");
        } else {
            log.info("Attempt to recreate the subscription.");
            final var group = mapper.getGroup(publishInterval);
            if (group == null || group.size() == 0) {
                throw new ConfigurationException(ExceptionContext.EMPTY_SUBSCRIPTION);
            }
            try {
                endpoint.deleteSubscription(publishInterval);
            } catch (OPCUAException ignored) {
                log.error("Could not delete subscription. Proceed with recreation.");
            }
            if (!subscribeToGroup(endpoint, group, group.getTagIds().values())) {
                throw new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION);
            }
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
                final var reading = failoverProxy.getEndpoint().read(e.getValue().getNodeId());
                endpointListener.onValueUpdate(e.getValue().getClientHandle(), reading.getValue(), reading.getKey());
            } catch (OPCUAException ex) {
                notRefreshable.add(e.getKey());
            }
        }
        if (!notRefreshable.isEmpty()) {
            log.error("An exception occurred when refreshing ISourceDataTags with IDs {}. ", notRefreshable);
        }
    }

    @Override
    public boolean subscribeToGroup(SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        return subscribeToGroup(failoverProxy.getEndpoint(), group, definitions);
    }

    private boolean subscribeToGroup(Endpoint endpoint, SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        try {
            boolean allSuccessful = true;
            final var handleQualityMap = endpoint.subscribeWithValueUpdateCallback(
                    group.getPublishInterval(),
                    definitions,
                    endpointListener::onValueUpdate);
            for (var e : handleQualityMap.entrySet()) {
                allSuccessful = allSuccessful && onSubscriptionComplete(e.getKey(), e.getValue());
            }
            return allSuccessful;
        } catch (OPCUAException ignored) {
            final String ids = Joiner.on(", ").join(definitions.stream().map(mapper::getTagId).toArray());
            log.error("Tags with IDs {} could not be subscribed. ", ids);
            return false;
        }
    }

    public boolean onSubscriptionComplete(UInteger clientHandle, SourceDataTagQuality quality) {
        final boolean valid = quality.isValid();
        if (valid) {
            final Long tagId = mapper.getTagId(clientHandle);
            if (tagId != null) {
                mapper.addTagToGroup(tagId);
            }
        } else {
            endpointListener.onTagInvalid(clientHandle, quality);
        }
        return valid;
    }
}
