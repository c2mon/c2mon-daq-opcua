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
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TagSubscriptionMapperTest extends MappingBase {

    @Test
    public void tagToNewGroupDoesNotAddDefinitionToGroup() {
        GroupDefinitionPair pair = mapper.toGroup(tag);

        assertFalse(pair.getSubscriptionGroup().contains(pair.getItemDefinition()));
    }

    @Test
    public void tagToNewGroupCreatesProperDefinition() {
        GroupDefinitionPair pair = mapper.toGroup(tag);

        assertEquals(ItemDefinition.of(tag), pair.getItemDefinition());
    }

    @Test
    public void tagToNewGroupCreatesProperDeadband() {
        GroupDefinitionPair pair = mapper.toGroup(tag);

        assertEquals(Deadband.of(tag), pair.getSubscriptionGroup().getDeadband());
    }

    @Test
    public void secondExecutionOfTagToGroupShouldReturnPriorPair() {
        GroupDefinitionPair pair1 = mapper.toGroup(tag);
        GroupDefinitionPair pair2 = mapper.toGroup(tag);

        assertEquals(pair1, pair2);
    }

    @Test
    public void tagsWithSameDeadbandToGroupShouldReturnSameGroup() {
        GroupDefinitionPair pair1 = mapper.toGroup(tag);
        GroupDefinitionPair pair2 = mapper.toGroup(tagWithSameDeadband);

        assertEquals(pair1.getSubscriptionGroup(), pair2.getSubscriptionGroup());
    }

    @Test
    public void tagsWithDifferentDeadbandsToGroupReturnDifferentGroup() {
        GroupDefinitionPair pair1 = mapper.toGroup(tag);
        GroupDefinitionPair pair2 = mapper.toGroup(tagWithDifferentDeadband);

        assertNotEquals(pair1.getSubscriptionGroup(), pair2.getSubscriptionGroup());
    }

    @Test
    public void tagsToGroupsShouldReturnAsManyGroupsAsDistinctDeadbands() {
        Map<SubscriptionGroup, List<ItemDefinition>> pairs = mapper.toGroups(Arrays.asList(tag, tagWithDifferentDeadband, tagWithSameDeadband));

        assertEquals(2, pairs.size());
    }

    @Test
    public void tagsToGroupsShouldCombineTagsWithSameDeadbands() {
        Map<SubscriptionGroup, List<ItemDefinition>> pairs = mapper.toGroups(Arrays.asList(tag, tagWithSameDeadband));

        SubscriptionGroup groupWithTwoTags = mapper.toGroup(tag).getSubscriptionGroup();
        List<ISourceDataTag> pairedTags = pairs
                .get(groupWithTwoTags)
                .stream()
                .map(ItemDefinition::getTag).collect(Collectors.toList());

        assertEquals(Arrays.asList(tag, tagWithSameDeadband), pairedTags);
    }

    @Test
    public void tagsToGroupsShouldSeparateTagsWithDifferentDeadbands() {
        Map<SubscriptionGroup, List<ItemDefinition>> pairs = mapper.toGroups(Arrays.asList(tag, tagWithDifferentDeadband));

        SubscriptionGroup group1 = mapper.toGroup(tag).getSubscriptionGroup();
        SubscriptionGroup group2 = mapper.toGroup(tagWithDifferentDeadband).getSubscriptionGroup();

        assertEquals(tag, pairs.get(group1).get(0).getTag());
        assertEquals(tagWithDifferentDeadband, pairs.get(group2).get(0).getTag());
    }

    @Test
    public void tagsToGroupsShouldNotAddTagsToGroups() {
        Map<SubscriptionGroup, List<ItemDefinition>> pairs = mapper.toGroups(Collections.singletonList(tag));
        assertFalse(pairs.keySet().stream().anyMatch(group -> group.contains(ItemDefinition.of(tag))));
    }

    @Test
    public void removeNewTagShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> mapper.removeTagFromGroup(tag));
    }

    @Test
    public void removeTagFromEmptyGroupShouldThrowError() {
        mapper.toGroup(tag);
        assertThrows(IllegalArgumentException.class, () -> mapper.removeTagFromGroup(tag));
    }

    @Test
    public void removeTagFromGroupGroupWithoutCorrespondingDefinition() {
        registerTagInGroup(tag);
        GroupDefinitionPair pair = mapper.removeTagFromGroup(tag);

        assertFalse(pair.getSubscriptionGroup().contains(pair.getItemDefinition()));
    }

    @Test
    public void getGroupsShouldReturnAllPreviouslyReturnedGroups() {
        Map<SubscriptionGroup, List<ItemDefinition>> pairs = mapper.toGroups(Arrays.asList(tag, tagWithDifferentDeadband, tagWithDifferentDeadband));

        Collection<SubscriptionGroup> groups = mapper.getGroups();

        assertTrue(groups.containsAll(pairs.keySet()));
        assertTrue(pairs.keySet().containsAll(groups));
    }

    @Test
    public void getDefinitionShouldReturnDefinitionWithCorrespondingTagReference() {
        ItemDefinition definition = mapper.getDefinition(tag);

        assertEquals(tag, definition.getTag());
    }

    @Test
    public void getDefinitionWithSameTagShouldReturnSameDefinitionTwice() {
        ItemDefinition definition = mapper.getDefinition(tag);

        assertEquals(definition.getTag(), mapper.getDefinition(tag).getTag());
    }

    @Test
    public void getDefinitionWithDifferentTagsShouldReturnDifferentDefinitions() {

        assertNotEquals(mapper.getDefinition(tag).getTag(), mapper.getDefinition(tagWithSameDeadband).getTag());
    }

    @Test
    public void registerDefinitionInGroupShouldAddDefinitonToGroup() {
        GroupDefinitionPair pair = registerTagInGroup(tag);

        assertTrue(pair.getSubscriptionGroup().contains(pair.getItemDefinition()));
    }

    @Test
    public void registerDefinitionTwiceShouldThrowError() {
        registerTagInGroup(tag);
        assertThrows(IllegalArgumentException.class, () -> registerTagInGroup(tag));
    }

    @Test
    public void registerRemoveRegisterDefinitionShouldAddDefinitionToGroup() {
        registerTagInGroup(tag);
        mapper.removeTagFromGroup(tag);
        GroupDefinitionPair pair = registerTagInGroup(tag);

        assertTrue(pair.getSubscriptionGroup().contains(pair.getItemDefinition()));
    }

    @Test
    public void registerDefinitionsInGroupShouldAddAllDefinitionsToGroup() {
        GroupDefinitionPair pair = mapper.toGroup(tag);
        ItemDefinition definitionWithSameDeadband = mapper.getDefinition(tagWithSameDeadband);
        mapper.registerDefinitionsInGroup(Arrays.asList(pair.getItemDefinition(), definitionWithSameDeadband));

        assertTrue(pair.getSubscriptionGroup().contains(pair.getItemDefinition()));
        assertTrue(pair.getSubscriptionGroup().contains(definitionWithSameDeadband));
    }

    @Test
    public void getTagByRandomClientHandleShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> mapper.getTagBy(UInteger.valueOf(2)));
    }

    @Test
    public void getTagByClientHandleShouldReturnProperTag() {
        ItemDefinition definition = mapper.getDefinition(tag);

        ISourceDataTag getTagBy = mapper.getTagBy(definition.getClientHandle());

        assertEquals(tag, getTagBy);
    }


    private GroupDefinitionPair registerTagInGroup(ISourceDataTag tag) {
        GroupDefinitionPair pair = mapper.toGroup(tag);
        mapper.registerDefinitionsInGroup(Collections.singletonList(pair.getItemDefinition()));
        return pair;
    }

}
