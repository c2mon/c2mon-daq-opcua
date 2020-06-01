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
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import com.google.common.base.Joiner;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component("controller")
@Slf4j
@RequiredArgsConstructor
public class ControllerImpl implements Controller {

    private final TagSubscriptionMapper mapper;
    private final EndpointListener endpointListener;
    private final Endpoint endpoint;
    private final AtomicBoolean stopped = new AtomicBoolean(true);

    /**
     * Attempt to connect to an OPC UA server and notify the {@link cern.c2mon.daq.common.IEquipmentMessageSender} about
     * a failure which prevents the DAQ from starting successfully.
     * @param uri the URI of the OPC UA server to connect to.
     * @throws OPCUAException       of type {@link CommunicationException} if an error not related to authentication
     *                              configuration occurs on connecting to the OPC UA server, and of type {@link
     *                              ConfigurationException} if it is not possible to connect to any of the the OPC UA
     *                              server's endpoints with the given authentication configuration settings.
     * @throws InterruptedException if the thread was interrupted on execution before the connection could be
     *                              established.
     */
    public void connect(String uri) throws OPCUAException, InterruptedException {
        try {
            endpoint.initialize(uri);
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
            endpoint.disconnect();
        } catch (OPCUAException e) {
            log.error("Error disconnecting: ", e);
        } catch (InterruptedException e) {
            log.error("Already stopping...", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns whether the controller has been intentionally shut down.
     * @return true if the controller's stop method has been called, or the initial connection was not successful.
     */
    @Override
    public boolean wasStopped() {
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
            throw new ConfigurationException(ConfigurationException.Cause.DATATAGS_EMPTY);
        }
        try {
            final ArrayList<Long> ids = new ArrayList<>();
            for (var e : mapper.mapTagsToGroupsAndDefinitions(dataTags).entrySet()) {
                if (!subscribeToGroup(e.getKey(), e.getValue())) {
                    ids.addAll(e.getValue().stream().map(mapper::getTagId).collect(Collectors.toList()));
                }
            }
            if (!ids.isEmpty()) {
                log.error("Could not subscribe DataTags with IDs {}.", Joiner.on(", ").join(ids));
            }
        } catch (InterruptedException e) {
            log.info("The thread was interrupted before all tags could be subscribed.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Subscribes to the OPC UA node corresponding to one data tag on the server.
     * @param sourceDataTag the ISourceDataTag to subscribe to.
     * @return true if the Tag could be subscribed successfully.
     * @throws InterruptedException if the thread was interrupted before the tag could be subscribed.
     */
    @Override
    public synchronized boolean subscribeTag(@NonNull final ISourceDataTag sourceDataTag) throws InterruptedException {
        SubscriptionGroup group = mapper.getGroup(sourceDataTag);
        DataTagDefinition definition = mapper.getOrCreateDefinition(sourceDataTag);
        return subscribeToGroup(group, Collections.singletonList(definition));
    }

    /**
     * Removes a Tag from the internal configuration and if already subscribed from the OPC UA subscription. It the
     * subscription is then empty, it is deleted along with the monitored item.
     * @param dataTag the tag to remove.
     * @return true if the tag was previously known.
     * @throws InterruptedException if the thread was interrupted before the tag could be removed.
     */
    @Override
    public synchronized boolean removeTag(final ISourceDataTag dataTag) throws InterruptedException {
        SubscriptionGroup group = mapper.getGroup(dataTag);
        boolean wasKnown = group != null && group.isSubscribed() && group.contains(dataTag);
        if (wasKnown) {
            log.info("Unsubscribing tag from server.");
            UaSubscription subscription = group.getSubscription();
            try {
                if (group.size() <= 1) {
                    endpoint.deleteSubscription(subscription);
                } else {
                    endpoint.deleteItemFromSubscription(mapper.getDefinition(dataTag.getId()).getClientHandle(), subscription);
                }
            } catch (OPCUAException e) {
                log.error("Deleting empty subscription could not be completed successfully.");
                return false;
            }
        }
        mapper.removeTag(dataTag);
        if (group != null && group.size() < 1) {
            group.reset();
        }
        return wasKnown;
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
     * subscription is recreated from scratch. If the controller received the command to stop, recreation is not
     * recreated and the method exists silently.
     * @param subscription the subscription of the old session which must be recreated.
     * @throws InterruptedException   if the thread was interrupted before the subscription could be recreated.
     * @throws CommunicationException if the subscription could not be recreated due to communication difficulty.
     * @throws ConfigurationException if the subscription can not be mapped to current DataTags, and therefore cannot be
     *                                recreated.
     */
    @Override
    public void recreateSubscription(UaSubscription subscription) throws InterruptedException, CommunicationException, ConfigurationException {
        log.info("entered");
        if (wasStopped()) {
            log.info("Controller was stopped, cease subscription recreation attempts.");
        } else {
            final var group = mapper.getGroup(subscription);
            if (group == null || group.size() == 0) {
                throw new ConfigurationException(ConfigurationException.Cause.EMPTY_SUBSCRIPTION);
            }
            try {
                endpoint.deleteSubscription(subscription);
            } catch (OPCUAException e) {
                log.error("Could not delete subscription. Proceed with recreation. ", e);
            }
            final var definitions = group.getTagIds()
                    .stream()
                    .map(id -> mapper.getTagIdDefinitionMap().get(id))
                    .collect(Collectors.toList());
            if (!subscribeToGroup(group, definitions)) {
                throw new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION);
            }
            group.reset();
        }
    }

    private synchronized void refresh(Map<Long, DataTagDefinition> entries) {
        final var notRefreshable = new ArrayList<Long>();
        try {
            for (var entry : entries.entrySet()) {
                try {
                    notifyListener(entry.getKey(), endpoint.read(entry.getValue().getNodeId()));
                } catch (OPCUAException e) {
                    notRefreshable.add(entry.getKey());
                }
            }
            if (!notRefreshable.isEmpty()) {
                log.error("An exception occurred when refreshing ISourceDataTags with IDs {}. ", notRefreshable);
            }
        } catch (InterruptedException e) {
            log.info("The thread was interrupted before all tags could be refreshed.");
            Thread.currentThread().interrupt();
        }
    }

    private boolean subscribeToGroup(SubscriptionGroup group, List<DataTagDefinition> definitions) throws InterruptedException {
        try {
            if (!group.isSubscribed() || !endpoint.isCurrent(group.getSubscription())) {
                group.setSubscription(endpoint.createSubscription(group.getPublishInterval()));
            }
            final var monitoredItems = endpoint.subscribeItem(group.getSubscription(), definitions, this::itemCreationCallback);
            return monitoredItems.stream().allMatch(this::subscriptionCompleted);
        } catch (OPCUAException e) {
            final String ids = Joiner.on(", ").join(definitions.stream().map(mapper::getTagId).toArray());
            log.error("Tags with IDs {} could not be subscribed. ", ids, e);
            return false;
        }
    }

    private boolean subscriptionCompleted(UaMonitoredItem item) {
        final Long tagId = mapper.getTagId(item.getClientHandle());
        final var statusCode = item.getStatusCode();
        if (statusCode.isBad()) {
            final var qualityCode = MiloMapper.getDataTagQualityCode(statusCode);
            endpointListener.onTagInvalid(tagId, new SourceDataTagQuality(qualityCode));
            return false;
        } else {
            mapper.addTagToGroup(tagId);
            return true;
        }
    }

    private void itemCreationCallback(UaMonitoredItem item, Integer i) {
        Long tag = mapper.getTagId(item.getClientHandle());
        item.setValueConsumer(value -> notifyListener(tag, value));
    }

    private void notifyListener(Long tagId, DataValue value) {
        SourceDataTagQualityCode tagQuality = MiloMapper.getDataTagQualityCode(value.getStatusCode());
        ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()), value.getSourceTime().getJavaTime());

        if (!tagQuality.equals(SourceDataTagQualityCode.OK)) {
            endpointListener.onTagInvalid(tagId, new SourceDataTagQuality(tagQuality));
        } else {
            endpointListener.onNewTagValue(tagId, valueUpdate, new SourceDataTagQuality(tagQuality));
        }
    }
}
