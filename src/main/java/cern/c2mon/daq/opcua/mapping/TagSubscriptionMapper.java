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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mapper maintains an internal state of the tags that are subscribed on the server(s) in {@link
 * SubscriptionGroup}s, and maps in between the {@link ItemDefinition} containing the Milo-compatible {@link
 * org.eclipse.milo.opcua.stack.core.types.builtin.NodeId}s, and {@link ISourceDataTag}s.
 */
@NoArgsConstructor
@Component("mapper")
@Getter
public class TagSubscriptionMapper implements TagSubscriptionManager {

    private final Map<Integer, SubscriptionGroup> subscriptionGroups = new ConcurrentHashMap<>();
    private final BiMap<Long, ItemDefinition> tagIdDefinitionMap = HashBiMap.create();

    /**
     * Gets a {@link SubscriptionGroup} with a publish interval equal to the timeDeadband, or creates a new one if none
     * yet exists.
     * @param timeDeadband to equal the publish interval of the group
     * @return the existing or newly created {@link SubscriptionGroup}
     */
    @Override
    public SubscriptionGroup getGroup(int timeDeadband) {
        if (groupExists(timeDeadband)) {
            return subscriptionGroups.get(timeDeadband);
        } else {
            SubscriptionGroup group = new SubscriptionGroup(timeDeadband);
            subscriptionGroups.put(timeDeadband, group);
            return group;
        }
    }

    /**
     * Returns all currently known {@link SubscriptionGroup}s. Usually called by the {@link
     * cern.c2mon.daq.opcua.connection.Endpoint} to recreate the state on the previously active server after a
     * failover.
     * @return all currently known {@link SubscriptionGroup}s
     */
    @Override
    public Collection<SubscriptionGroup> getGroups() {
        return subscriptionGroups.values();
    }

    /**
     * Returns the {@link ItemDefinition} associated with the tagId, or null if no {@link ItemDefinition} is associated
     * with the tagId.
     * @param tagId the tag ID whose associated {@link ItemDefinition} is to be returned
     * @return the {@link ItemDefinition} to which the tagId is mapped, or null if no {@link ItemDefinition} is
     * associated with the tagId
     */
    @Override
    public ItemDefinition getDefinition(Long tagId) {
        return tagIdDefinitionMap.get(tagId);
    }

    /**
     * Returns an {@link Optional} tagId associated with the {@link ItemDefinition} identified by the clientHandle. If
     * no tagId is associated with the clientHandle, an empty {@link Optional} is returned.
     * @param clientHandle the client handle identifying the {@link ItemDefinition} whose associated tagId is to be
     *                     returned
     * @return the tagId associated with the clientHandle
     */
    @Override
    public Optional<Long> getTagId(UInteger clientHandle) {
        return tagIdDefinitionMap.entrySet().stream()
                .filter(e -> e.getValue().getClientHandle().equals(clientHandle))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Returns the {@link ItemDefinition} associated with the tagId, or created a new one {@link ItemDefinition} if none
     * is yet associated with the tagId.
     * @param tag the {@link ISourceDataTag} whose associated {@link ItemDefinition} is to be returned, or for whom a
     *            new {@link ItemDefinition} shall be created
     * @return the {@link ItemDefinition} to which the tagId is mapped, or the newly created {@link ItemDefinition} for
     * the tag.
     */
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

    /**
     * Registers the tagId as subscribed and adds the associated {@link ItemDefinition} to the appropriate {@link
     * SubscriptionGroup}.
     * @param tagId the tagId whose associated {@link ItemDefinition} to add to the appropriate {@link
     *              SubscriptionGroup}
     */
    @Override
    public void addTagToGroup(long tagId) {
        if (tagIdDefinitionMap.containsKey(tagId)) {
            final SubscriptionGroup group = getGroup(tagIdDefinitionMap.get(tagId).getTimeDeadband());
            group.add(tagId, tagIdDefinitionMap.get(tagId));
        }
    }

    /**
     * Registers the tagId as unsubscribed and removes the associated {@link ItemDefinition} from the corresponding
     * {@link SubscriptionGroup}.
     * @param tagId the tagId whose associated {@link ItemDefinition} to remove its {@link SubscriptionGroup}
     * @return true if the tagId was previously associated with an {@link ItemDefinition} which was part of a {@link
     * SubscriptionGroup}.
     */
    @Override
    public boolean removeTag(long tagId) {
        ItemDefinition definition = tagIdDefinitionMap.remove(tagId);
        if (definition == null) {
            return false;
        }
        final SubscriptionGroup group = subscriptionGroups.get(definition.getTimeDeadband());
        return group != null && group.remove(tagId);
    }

    /**
     * Removes all managed {@link ItemDefinition}s and {@link SubscriptionGroup}s from the internal state.
     */
    @Override
    public void clear() {
        tagIdDefinitionMap.clear();
        subscriptionGroups.clear();
    }

    private boolean groupExists(int deadband) {
        return subscriptionGroups.get(deadband) != null;
    }
}
