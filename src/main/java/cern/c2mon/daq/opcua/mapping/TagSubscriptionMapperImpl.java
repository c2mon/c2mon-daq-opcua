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
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TagSubscriptionMapperImpl implements TagSubscriptionMapper {

    private final Map<Deadband, SubscriptionGroup> subscriptionGroups = new ConcurrentHashMap<>();

    // TODO: this could be avoided (only useful for clientHandle) -> what is quicker?
    private final Map<ISourceDataTag, ItemDefinition> tag2Definition = new ConcurrentHashMap<>();

    public boolean isSubscribed(ISourceDataTag tag) {
        SubscriptionGroup group = getGroup(Deadband.of(tag));
        return group.isSubscribed() && group.contains(getDefinition(tag));
    }

    @Override
    public void clear() {
        subscriptionGroups.clear();
        tag2Definition.clear();
    }

    @Override
    public Map<SubscriptionGroup, List<ItemDefinition>> toGroups(Collection<ISourceDataTag> tags) {
        return tags
                .stream()
                .collect(Collectors.groupingBy(Deadband::of))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> getOrCreateGroup(e.getKey()), e -> getOrMakeDefinitionsFrom(e.getValue())));
    }

    @Override
    public GroupDefinitionPair toGroup(ISourceDataTag tag) {
        SubscriptionGroup group = getOrCreateGroup(Deadband.of(tag));
        ItemDefinition definition = getOrMakeDefinitionsFrom(Collections.singletonList(tag)).get(0);
        return GroupDefinitionPair.of(group, definition);
    }

    @Override
    public void addTagToGroup(ISourceDataTag tag) {
        registerDefinitionInGroup(getDefinition(tag));
    }

    @Override
    public GroupDefinitionPair removeTagFromGroup(ISourceDataTag dataTag) {
        ItemDefinition definition = tag2Definition.remove(dataTag);
        if (definition == null) {
            throw new IllegalArgumentException("The tag cannot be removed, since it has not been added to the endpoint.");
        }
        GroupDefinitionPair pair = toGroup(dataTag);
        SubscriptionGroup subscriptionGroup = pair.getSubscriptionGroup();
        ItemDefinition itemDefinition = pair.getItemDefinition();

        if (!subscriptionGroup.contains(itemDefinition)) {
            throw new IllegalArgumentException("The tag cannot be removed, since it has not been registered in a subscription group.");
        }
        subscriptionGroup.remove(itemDefinition);
        return pair;
    }

    @Override
    public Collection<SubscriptionGroup> getGroups() {
        return subscriptionGroups.values();
    }

    @Override
    public ItemDefinition getDefinition(ISourceDataTag tag) {
        return getOrMakeDefinitionFrom(tag);
    }

    @Override
    public void registerDefinitionInGroup(ItemDefinition definition) {
        getGroupBy(definition).add(definition);
    }

    @Override
    public void registerDefinitionsInGroup(Collection<ItemDefinition> definitions) {
        definitions
                .stream()
                .collect(Collectors.groupingBy(itemDefinition -> Deadband.of(itemDefinition.getTag())))
                .forEach((deadband, definitionList) -> definitionList.forEach(i -> getOrCreateGroup(deadband).add(i)));
    }

    @Override
    public ISourceDataTag getTagBy(UInteger clientHandle) {
        return findDefinitionWithClientHandle(clientHandle).getTag();
    }

    private SubscriptionGroup getGroupBy(ItemDefinition definition) {
        return getOrCreateGroup(Deadband.of(definition.getTag()));
    }

    private ItemDefinition findDefinitionWithClientHandle(UInteger clientHandle){
        Optional<ItemDefinition> definitionWithMatchingHandle = tag2Definition
                .values()
                .stream()
                .filter(def -> def.getClientHandle().equals(clientHandle))
                .findAny();

        if(!definitionWithMatchingHandle.isPresent()) {
            throw new IllegalArgumentException("This clientHandle is not associated with any tag.");
        }
        return definitionWithMatchingHandle.get();
    }

    private SubscriptionGroup getOrCreateGroup(Deadband deadband) {
        return hasGroup(deadband) ? getGroup(deadband) : createGroup(deadband);
    }

    private boolean hasGroup(Deadband deadband) {
        return getGroup(deadband) != null;
    }

    public SubscriptionGroup getGroup(Deadband deadband) {
        return subscriptionGroups.get(deadband);
    }

    private SubscriptionGroup createGroup(Deadband deadband) {
        SubscriptionGroup group = new SubscriptionGroup(deadband);
        subscriptionGroups.put(deadband, group);
        return group;
    }

    private List<ItemDefinition> getOrMakeDefinitionsFrom(Collection<ISourceDataTag> tags) {
        List<ItemDefinition> result = new ArrayList<>();
        for(ISourceDataTag tag : tags) {
            result.add(getOrMakeDefinitionFrom(tag));
        }
        return result;

    }

    private ItemDefinition getOrMakeDefinitionFrom(ISourceDataTag tag) {
        if (tag2Definition.containsKey(tag)) {
            return tag2Definition.get(tag);
        } else {
            ItemDefinition definition = ItemDefinition.of(tag);
            tag2Definition.put(tag, definition);
            return definition;
        }
    }
}
