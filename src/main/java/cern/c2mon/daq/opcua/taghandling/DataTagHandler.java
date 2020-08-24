package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

/**
 * The {@link DataTagHandler} is responsible for managing the state of subscribed {@link ISourceDataTag}s in {@link
 * TagSubscriptionMapper}, and triggers the subscription to or removal of subscriptions of {@link ISourceDataTag}s from
 * the server.
 */
@Component("dataTagHandler")
@Slf4j
@RequiredArgsConstructor
@ManagedResource
public class DataTagHandler implements IDataTagHandler {

    private final TagSubscriptionManager manager;
    private final MessageSender messageSender;
    private final Controller controller;

    @Override
    public void subscribeTags(final Collection<ISourceDataTag> dataTags) {
        final Function<List<ISourceDataTag>, List<ItemDefinition>> definitionsToTags = e -> e.stream()
                .map(t -> {
                    try {
                        return manager.getOrCreateDefinition(t);
                    } catch (ConfigurationException ex) {
                        log.error("Cannot subscribe the Tag with ID {}: incorrect hardware address!", t.getId(), ex);
                        return null;
                    }
                }).filter(Objects::nonNull)
                .collect(toList());
        final Map<SubscriptionGroup, List<ItemDefinition>> definitionsByGroups = dataTags.stream()
                .collect(groupingBy(ISourceDataTag::getTimeDeadband))
                .entrySet().stream()
                .collect(toMap(e -> manager.getGroup(e.getKey()), e -> definitionsToTags.apply(e.getValue())));
        synchronized (this) {
            final Map<Integer, SourceDataTagQuality> handleQualityMap = controller.subscribe(definitionsByGroups);
            handleQualityMap.forEach(this::completeSubscriptionAndReportSuccess);
        }
    }

    @Override
    public boolean subscribeTag(final ISourceDataTag sourceDataTag) {
        ItemDefinition definition;
        try {
            definition = manager.getOrCreateDefinition(sourceDataTag);
        } catch (ConfigurationException e) {
            log.error("The Tag with ID {} has an incorrect hardware address and cannot be refreshed.", sourceDataTag.getId(), e);
            return false;
        }
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
        final boolean wasSubscribed = group.contains(dataTag.getId());
        if (wasSubscribed) {
            log.info("Unsubscribing tag from server.");
            final ItemDefinition definition = manager.getDefinition(dataTag.getId());
            if (!controller.unsubscribe(definition)) {
                return false;
            }
        }
        manager.removeTag(dataTag.getId());
        return wasSubscribed;
    }

    @Override
    @ManagedOperation
    public void refreshAllDataTags() {
        refresh(manager.getTagIdDefinitionMap());
    }

    @Override
    public void refreshDataTag(ISourceDataTag sourceDataTag) {
        try {
            final ItemDefinition definition = manager.getOrCreateDefinition(sourceDataTag);
            final Map<Long, ItemDefinition> singleEntryMap = new ConcurrentHashMap<>();
            singleEntryMap.put(sourceDataTag.getId(), definition);
            refresh(singleEntryMap);
        } catch (ConfigurationException e) {
            log.error("The Tag with ID {} has an incorrect hardware address and cannot be refreshed.", sourceDataTag.getId(), e);
        }
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