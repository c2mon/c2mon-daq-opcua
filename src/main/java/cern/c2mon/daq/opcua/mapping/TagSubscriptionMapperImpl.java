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

import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.NoArgsConstructor;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.*;

@NoArgsConstructor
@Component("mapper")
public class TagSubscriptionMapperImpl implements TagSubscriptionMapper {

    private final Map<Deadband, SubscriptionGroup> subscriptionGroups = new ConcurrentHashMap<>();

    private final Map<ISourceDataTag, DataTagDefinition> tag2Definition = new ConcurrentHashMap<>();
    private final Map<ISourceCommandTag, CommandTagDefinition> command2Definition = new ConcurrentHashMap<>();

    @Override
    public Collection<SubscriptionGroup> getGroups() {
        return subscriptionGroups.values();
    }

    @Override
    public Map<SubscriptionGroup, List<DataTagDefinition>> maptoGroupsWithDefinitions (Collection<ISourceDataTag> tags) {
        return tags
                .stream()
                .collect(groupingBy(Deadband::of))
                .entrySet()
                .stream()
                .collect(toMap(e -> getOrCreateGroup(e.getKey()), e -> getOrMakeDefinitionsFrom(e.getValue())));
    }

    @Override
    public SubscriptionGroup getGroup (ISourceDataTag tag) {
        return getOrCreateGroup(Deadband.of(tag));
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
        return findDefinitionWithClientHandle(clientHandle).getTag();
    }

    @Override
    public void addDefinitionsToGroups (Collection<DataTagDefinition> definitions) {
        definitions
                .stream()
                .collect(groupingBy(itemDefinition -> Deadband.of(itemDefinition.getTag())))
                .forEach((deadband, definitionList) -> definitionList.forEach(i -> getOrCreateGroup(deadband).add(i)));
    }

    @Override
    public void addDefinitionToGroup (DataTagDefinition definition) {
        getOrCreateGroup(Deadband.of(definition.getTag())).add(definition);
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
        SubscriptionGroup group = subscriptionGroups.get(Deadband.of(tag));
        return group != null && group.isSubscribed() && group.contains(getDefinition(tag));
    }

    @Override
    public void clear() {
        subscriptionGroups.clear();
        tag2Definition.clear();
    }

    @Override
    public CommandTagDefinition getDefinition(ISourceCommandTag tag) {
        if (command2Definition.containsKey(tag)) {
            return command2Definition.get(tag);
        } else {
            CommandTagDefinition definition = ItemDefinition.of(tag);
            command2Definition.put(tag, definition);
            return definition;
        }
    }

    private SubscriptionGroup getOrCreateGroup(Deadband deadband) {
        if (groupExists(deadband)) {
            return subscriptionGroups.get(deadband);
        } else {
            SubscriptionGroup group = new SubscriptionGroup(deadband);
            subscriptionGroups.put(deadband, group);
            return group;
        }
    }

    private boolean groupExists (Deadband deadband) {
        return subscriptionGroups.get(deadband) != null;
    }

    private DataTagDefinition findDefinitionWithClientHandle(UInteger clientHandle){
        Optional<DataTagDefinition> definitionWithMatchingHandle = tag2Definition
                .values()
                .stream()
                .filter(def -> def.getClientHandle().equals(clientHandle))
                .findAny();

        if(!definitionWithMatchingHandle.isPresent()) {
            throw new IllegalArgumentException("This clientHandle is not associated with any tag.");
        }
        return definitionWithMatchingHandle.get();
    }

    private List<DataTagDefinition> getOrMakeDefinitionsFrom(Collection<ISourceDataTag> tags) {
        List<DataTagDefinition> result = new ArrayList<>();
        for (ISourceDataTag tag : tags) {
            result.add(getDefinition(tag));
        }
        return result;

    }

}
