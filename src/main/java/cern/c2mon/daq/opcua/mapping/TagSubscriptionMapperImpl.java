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

    private final Map<ISourceDataTag, DataTagDefinition> tag2Definition = new ConcurrentHashMap<>();

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
    public DataTagDefinition getDefinition(ISourceDataTag tag) {
        if (tag2Definition.containsKey(tag)) {
            return tag2Definition.get(tag);
        } else {
            DataTagDefinition definition = ItemDefinition.of(tag);
            tag2Definition.put(tag, definition);
            return definition;
        }
    }

    @Override
    public ISourceDataTag getTag (UInteger clientHandle) {
        Optional<DataTagDefinition> definitionWithMatchingHandle = tag2Definition
                .values()
                .stream()
                .filter(def -> def.getClientHandle().equals(clientHandle))
                .findAny();

        if(!definitionWithMatchingHandle.isPresent()) {
            throw new IllegalArgumentException("This clientHandle is not associated with any tag.");
        }
        return definitionWithMatchingHandle.get().getTag();
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
    public void addTagToGroup(ISourceDataTag tag) {
        addDefinitionToGroup(getDefinition(tag));
    }

    @Override
    public void removeTagFromGroup(ISourceDataTag dataTag) {
        DataTagDefinition definition = tag2Definition.remove(dataTag);
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
        return group != null && group.isSubscribed() && group.contains(getDefinition(tag));
    }

    @Override
    public void clear() {
        subscriptionGroups.clear();
        tag2Definition.clear();
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
            result.add(getDefinition(tag));
        }
        return result;

    }

}
