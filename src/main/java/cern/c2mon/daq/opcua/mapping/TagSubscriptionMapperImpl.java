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
import lombok.NoArgsConstructor;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@NoArgsConstructor
@Component("mapper")
public class TagSubscriptionMapperImpl implements TagSubscriptionMapper {

    private final Map<Integer, SubscriptionGroup> subscriptionGroups = new ConcurrentHashMap<>();

    private final Map<Long, DataTagDefinition> tagIdToDefinition = new ConcurrentHashMap<>();

    @Override
    public Collection<SubscriptionGroup> getGroups() {
        return subscriptionGroups.values();
    }

    @Override
    public Map<SubscriptionGroup, List<DataTagDefinition>> maptoGroupsWithDefinitions (Collection<ISourceDataTag> tags) {
        return tags
                .stream()
                .collect(groupingBy(ISourceDataTag::getTimeDeadband))
                .entrySet()
                .stream()
                .collect(toMap(e -> getOrCreateGroup(e.getKey()), e -> getOrMakeDefinitionsFrom(e.getValue())));
    }

    @Override
    public SubscriptionGroup getGroup (ISourceDataTag tag) {
        return getOrCreateGroup(tag.getTimeDeadband());
    }

    @Override
    public SubscriptionGroup getGroup (UaSubscription subscription) {
        for(SubscriptionGroup group : getGroups()) {
            if (group.getSubscription().equals(subscription)) {
                return  group;
            }
        }
        throw new IllegalArgumentException("This subscription does not correspond to a subscription group.");
    }

    @Override
    public DataTagDefinition getOrCreateDefinition(ISourceDataTag tag) {
        if (tagIdToDefinition.containsKey(tag.getId())) {
            return tagIdToDefinition.get(tag.getId());
        } else {
            final DataTagDefinition definition = ItemDefinition.of(tag);
            tagIdToDefinition.put(tag.getId(), definition);
            return definition;
        }
    }

    @Override
    public DataTagDefinition getOrCreateDefinition(Long tagId) {
        if (tagIdToDefinition.containsKey(tagId)) {
            return tagIdToDefinition.get(tagId);
        } else {
            throw new IllegalArgumentException("The tag id is unknown.");
        }
    }

    @Override
    public Long getTagId(UInteger clientHandle) {
        final Optional<Map.Entry<Long, DataTagDefinition>> mapEntry = tagIdToDefinition.entrySet()
                .stream()
                .filter(e -> e.getValue().getClientHandle().equals(clientHandle))
                .findFirst();

        if(!mapEntry.isPresent()) {
            throw new IllegalArgumentException("This clientHandle is not associated with mapEntry tag.");
        }
        return mapEntry.get().getKey();
    }

    @Override
    public void addDefinitionsToGroups (Collection<DataTagDefinition> definitions) {
        definitions
                .stream()
                .collect(groupingBy(itemDefinition -> itemDefinition.getTag().getTimeDeadband()))
                .forEach((timeDB, definitionList) -> definitionList.forEach(i -> getOrCreateGroup(timeDB).add(i)));
    }

    @Override
    public void addDefinitionToGroup (DataTagDefinition definition) {
        getOrCreateGroup(definition.getTag().getTimeDeadband()).add(definition);
    }

    @Override
    public void addTagToGroup(Long tagId) {
        addDefinitionToGroup(getOrCreateDefinition(tagId));
    }

    @Override
    public void removeTagFromGroup(ISourceDataTag dataTag) {
        DataTagDefinition definition = tagIdToDefinition.get(dataTag.getId());
        if (definition == null) {
            throw new IllegalArgumentException("The tag cannot be removed, since it has not been added to the endpoint.");
        }
        SubscriptionGroup subscriptionGroup = getGroup(dataTag);

        if (!subscriptionGroup.contains(definition)) {
            throw new IllegalArgumentException("The tag cannot be removed, since it has not been registered in a subscription group.");
        }
        subscriptionGroup.remove(definition);
    }

    @Override
    public boolean isSubscribed(ISourceDataTag tag) {
        SubscriptionGroup group = subscriptionGroups.get(tag.getTimeDeadband());
        return group != null && group.isSubscribed() && group.contains(getOrCreateDefinition(tag.getId()));
    }

    @Override
    public void clear() {
        subscriptionGroups.clear();
        tagIdToDefinition.clear();
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

    private List<DataTagDefinition> getOrMakeDefinitionsFrom(Collection<ISourceDataTag> tags) {
        List<DataTagDefinition> result = new ArrayList<>();
        for (ISourceDataTag tag : tags) {
            result.add(getOrCreateDefinition(tag));
        }
        return result;

    }

}
