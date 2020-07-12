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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@NoArgsConstructor
@Component("mapper")
@Getter
public class TagSubscriptionMapperImpl implements TagSubscriptionMapper {

    private final Map<Integer, SubscriptionGroup> subscriptionGroups = new ConcurrentHashMap<>();
    private final BiMap<Long, ItemDefinition> tagIdDefinitionMap = HashBiMap.create();

    @Override
    public Map<SubscriptionGroup, List<ItemDefinition>> mapToGroups(Collection<ItemDefinition> definitions) {
        return definitions
                .stream()
                .collect(groupingBy(ItemDefinition::getTimeDeadband))
                .entrySet()
                .stream()
                .collect(toMap(e -> getOrCreateGroup(e.getKey()),Map.Entry::getValue));
    }

    @Override
    public SubscriptionGroup getGroup(ISourceDataTag dataTag) {
        getOrCreateDefinition(dataTag);
        return getOrCreateGroup(dataTag.getTimeDeadband());
    }


    @Override
    public SubscriptionGroup getGroup(int timeDeadband) {
        return subscriptionGroups.get(timeDeadband);
    }

    @Override
    public ItemDefinition getOrCreateDefinition(ISourceDataTag tag) {
        if (tagIdDefinitionMap.containsKey(tag.getId())) {
            return tagIdDefinitionMap.get(tag.getId());
        } else {
            final ItemDefinition definition = ItemDefinition.of(tag);
            tagIdDefinitionMap.put(tag.getId(), definition);
            return definition;
        }
    }

    @Override
    public ItemDefinition getDefinition(Long tagId) {
        if (tagIdDefinitionMap.containsKey(tagId)) {
            return tagIdDefinitionMap.get(tagId);
        } else {
            throw new IllegalArgumentException("The tag id is unknown.");
        }
    }

    @Override
    public long getTagId(ItemDefinition definition) {
        return tagIdDefinitionMap.inverse().get(definition);
    }


    @Override
    public Long getTagId(UInteger clientHandle) {
        return tagIdDefinitionMap.entrySet().stream()
                .filter(e -> e.getValue().getClientHandle().equals(clientHandle))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void addTagToGroup(Long tagId) {
        final SubscriptionGroup group = getOrCreateGroup(tagIdDefinitionMap.get(tagId).getTimeDeadband());
        group.add(tagId, tagIdDefinitionMap.get(tagId));
    }


    @Override
    public boolean removeTag(ISourceDataTag dataTag) {
        ItemDefinition definition = tagIdDefinitionMap.remove(dataTag.getId());
        if (definition == null) {
            return false;
        }
        final SubscriptionGroup group = subscriptionGroups.get(dataTag.getTimeDeadband());
        final boolean wasSubscribed = group != null && group.remove(dataTag);
        if (group != null && group.size() < 1) {
            group.reset();
        }
        return wasSubscribed;
    }

    @Override
    public void clear() {
        tagIdDefinitionMap.clear();
        subscriptionGroups.clear();
    }

    @Override
    public boolean wasSubscribed(ISourceDataTag tag) {
        final SubscriptionGroup group = getGroup(tag.getTimeDeadband());
        return group != null && group.contains(tag);
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

    private boolean groupExists(int deadband) {
        return subscriptionGroups.get(deadband) != null;
    }
}
