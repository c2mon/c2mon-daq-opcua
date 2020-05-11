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
package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@NoArgsConstructor
@Component("mapper")
public class TagSubscriptionMapperImpl implements TagSubscriptionMapper {

    private final Map<Integer, SubscriptionGroup> subscriptionGroups = new ConcurrentHashMap<>();

    @Getter
    private final Map<Long, DataTagDefinition> tagIdDefinitionMap = new ConcurrentHashMap<>();

    @Override
    public Map<SubscriptionGroup, List<DataTagDefinition>> mapTagsToGroupsAndDefinitions(Collection<ISourceDataTag> tags) {
        return tags
                .stream()
                .collect(groupingBy(ISourceDataTag::getTimeDeadband))
                .entrySet()
                .stream()
                .collect(toMap(e -> getOrCreateGroup(e.getKey()), e -> e.getValue().stream()
                        .map(this::getOrCreateDefinition)
                        .collect(Collectors.toList())));
    }

    @Override
    public void addToTagDefinitionMap(Map<Long, DataTagDefinition> newMap) {
        newMap.forEach(tagIdDefinitionMap::putIfAbsent);
    }

    @Override
    public Map<SubscriptionGroup, List<DataTagDefinition>> mapToGroups(Collection<DataTagDefinition> definitions) {
        return definitions
                .stream()
                .collect(groupingBy(DataTagDefinition::getTimeDeadband))
                .entrySet()
                .stream()
                .collect(toMap(e -> getOrCreateGroup(e.getKey()), Map.Entry::getValue));
    }

    @Override
    public SubscriptionGroup getGroup (ISourceDataTag tag) {
        getOrCreateDefinition(tag);
        return getOrCreateGroup(tag.getTimeDeadband());
    }

    @Override
    public SubscriptionGroup getGroup (UaSubscription subscription) {
        for(SubscriptionGroup group : subscriptionGroups.values()) {
            if (group.getSubscription().equals(subscription)) {
                return  group;
            }
        }
        throw new IllegalArgumentException("This subscription does not correspond to a subscription group.");
    }

    @Override
    public DataTagDefinition getOrCreateDefinition(ISourceDataTag tag) {
        if (tagIdDefinitionMap.containsKey(tag.getId())) {
            return tagIdDefinitionMap.get(tag.getId());
        } else {
            final DataTagDefinition definition = ItemDefinition.of(tag);
            tagIdDefinitionMap.put(tag.getId(), definition);
            return definition;
        }
    }

    @Override
    public DataTagDefinition getDefinition(Long tagId) {
        if (tagIdDefinitionMap.containsKey(tagId)) {
            return tagIdDefinitionMap.get(tagId);
        } else {
            throw new IllegalArgumentException("The tag id is unknown.");
        }
    }

    @Override
    public Long getTagId(UInteger clientHandle) {
        final Optional<Map.Entry<Long, DataTagDefinition>> mapEntry = tagIdDefinitionMap.entrySet().stream()
                .filter(e -> e.getValue().getClientHandle().equals(clientHandle))
                .findFirst();

        if(mapEntry.isEmpty()) {
            throw new IllegalArgumentException("This clientHandle is not associated with mapEntry tag.");
        }
        return mapEntry.get().getKey();
    }

    @Override
    public void addTagToGroup(Long tagId) {
        final SubscriptionGroup group = getOrCreateGroup(tagIdDefinitionMap.get(tagId).getTimeDeadband());
        group.add(tagId);
    }


    @Override
    public boolean removeTag(ISourceDataTag dataTag) {
        DataTagDefinition definition = tagIdDefinitionMap.get(dataTag.getId());
        if (definition == null) {
            return false;
        }
        tagIdDefinitionMap.remove(dataTag.getId());
        SubscriptionGroup subscriptionGroup = getGroup(dataTag);
        if (!subscriptionGroup.contains(dataTag)) {
            return false;
        }
        subscriptionGroup.remove(dataTag);
        return true;
    }

    @Override
    public boolean isSubscribed(ISourceDataTag tag) {
        SubscriptionGroup group = subscriptionGroups.get(tag.getTimeDeadband());
        return group != null && group.isSubscribed() && group.contains(tag);
    }

    @Override
    public void clear() {
        tagIdDefinitionMap.clear();
        subscriptionGroups.clear();
    }

    private SubscriptionGroup getOrCreateGroup(int timeDeadband) {
        if (groupExists(timeDeadband)) {
            return subscriptionGroups.get(timeDeadband);
        } else {
            SubscriptionGroup group = new SubscriptionGroup(timeDeadband);
            subscriptionGroups.put(timeDeadband, group);
            return group;
        }
    }

    private boolean groupExists (int deadband) {
        return subscriptionGroups.get(deadband) != null;
    }
}
