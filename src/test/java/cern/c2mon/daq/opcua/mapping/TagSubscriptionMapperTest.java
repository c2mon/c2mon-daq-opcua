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
        SubscriptionGroup group = mapper.getGroup(tag);
        DataTagDefinition definition = mapper.getDefinition(tag);

        assertFalse(group.contains(definition));
    }

    @Test
    public void tagToNewGroupCreatesProperDefinition() {
        DataTagDefinition definition = mapper.getDefinition(tag);

        assertEquals(DataTagDefinition.of(tag), definition);
    }

    @Test
    public void tagToNewGroupCreatesProperDeadband() {
        SubscriptionGroup group = mapper.getGroup(tag);

        assertEquals(tag.getTimeDeadband(), group.getPublishInterval());
    }

    @Test
    public void secondExecutionOfTagToGroupShouldReturnPriorGroup() {
        SubscriptionGroup group1 = mapper.getGroup(tag);
        SubscriptionGroup group2 = mapper.getGroup(tag);

        assertEquals(group1, group2);
    }

    @Test
    public void secondExecutionOfTagToDefinitionShouldReturnPriorDefinition() {
        DataTagDefinition definition1 = mapper.getDefinition(tag);
        DataTagDefinition definition2 = mapper.getDefinition(tag);

        assertEquals(definition1, definition2);
    }
    @Test
    public void tagsWithSameDeadbandToGroupShouldReturnSameGroup() {
        SubscriptionGroup group1 = mapper.getGroup(tag);
        SubscriptionGroup group2 = mapper.getGroup(tagWithSameDeadband);

        assertEquals(group1, group2);
    }

    @Test
    public void tagsWithDifferentDeadbandsToGroupReturnDifferentGroup() {
        SubscriptionGroup group1 = mapper.getGroup(tag);
        SubscriptionGroup group2 = mapper.getGroup(tagWithDifferentDeadband);

        assertNotEquals(group1, group2);
    }

    @Test
    public void tagsToGroupsShouldReturnAsManyGroupsAsDistinctDeadbands() {
        Map<SubscriptionGroup, List<DataTagDefinition>> pairs = mapper.maptoGroupsWithDefinitions(Arrays.asList(tag, tagWithDifferentDeadband, tagWithSameDeadband));

        assertEquals(2, pairs.size());
    }

    @Test
    public void tagsToGroupsShouldCombineTagsWithSameDeadbands() {
        Map<SubscriptionGroup, List<DataTagDefinition>> pairs = mapper.maptoGroupsWithDefinitions(Arrays.asList(tag, tagWithSameDeadband));

        SubscriptionGroup groupWithTwoTags = mapper.getGroup(tag);
        List<ISourceDataTag> pairedTags = pairs
                .get(groupWithTwoTags)
                .stream()
                .map(DataTagDefinition::getTag).collect(Collectors.toList());

        assertEquals(Arrays.asList(tag, tagWithSameDeadband), pairedTags);
    }

    @Test
    public void tagsToGroupsShouldSeparateTagsWithDifferentDeadbands() {
        Map<SubscriptionGroup, List<DataTagDefinition>> pairs = mapper.maptoGroupsWithDefinitions(Arrays.asList(tag, tagWithDifferentDeadband));

        SubscriptionGroup group1 = mapper.getGroup(tag);
        SubscriptionGroup group2 = mapper.getGroup(tagWithDifferentDeadband);

        assertEquals(tag, pairs.get(group1).get(0).getTag());
        assertEquals(tagWithDifferentDeadband, pairs.get(group2).get(0).getTag());
    }

    @Test
    public void tagsToGroupsShouldNotAddTagsToGroups() {
        Map<SubscriptionGroup, List<DataTagDefinition>> pairs = mapper.maptoGroupsWithDefinitions(Collections.singletonList(tag));
        assertFalse(pairs.keySet().stream().anyMatch(group -> group.contains(DataTagDefinition.of(tag))));
    }

    @Test
    public void removeNewTagShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> mapper.removeTagFromGroup(tag));
    }

    @Test
    public void removeTagFromEmptyGroupShouldThrowError() {
        mapper.getGroup(tag);
        assertThrows(IllegalArgumentException.class, () -> mapper.removeTagFromGroup(tag));
    }

    @Test
    public void removeTagFromGroupShouldDeleteCorrespondingDefinition() {
        SubscriptionGroup group = mapper.getGroup(tag);
        DataTagDefinition definition = mapper.getDefinition(tag);
        mapper.addDefinitionsToGroups(Collections.singletonList(definition));

        mapper.removeTagFromGroup(tag);

        assertFalse(group.contains(definition));
    }

    @Test
    public void getGroupsShouldReturnAllPreviouslyReturnedGroups() {
        Map<SubscriptionGroup, List<DataTagDefinition>> pairs = mapper.maptoGroupsWithDefinitions(Arrays.asList(tag, tagWithDifferentDeadband, tagWithDifferentDeadband));

        Collection<SubscriptionGroup> groups = mapper.getGroups();

        assertTrue(groups.containsAll(pairs.keySet()));
        assertTrue(pairs.keySet().containsAll(groups));
    }

    @Test
    public void getDefinitionShouldReturnDefinitionWithCorrespondingTagReference() {
        DataTagDefinition definition = mapper.getDefinition(tag);

        assertEquals(tag, definition.getTag());
    }

    @Test
    public void getDefinitionWithSameTagShouldReturnSameDefinitionTwice() {
        DataTagDefinition definition = mapper.getDefinition(tag);

        assertEquals(definition.getTag(), mapper.getDefinition(tag).getTag());
    }

    @Test
    public void getDefinitionWithDifferentTagsShouldReturnDifferentDefinitions() {

        assertNotEquals(mapper.getDefinition(tag).getTag(), mapper.getDefinition(tagWithSameDeadband).getTag());
    }

    @Test
    public void registerDefinitionInGroupShouldAddDefinitonToGroup() {
        SubscriptionGroup group = mapper.getGroup(tag);
        DataTagDefinition definition = mapper.getDefinition(tag);
        mapper.addDefinitionsToGroups(Collections.singletonList(definition));

        assertTrue(group.contains(definition));
    }

    @Test
    public void registerDefinitionTwiceShouldThrowError() {
        DataTagDefinition definition = mapper.getDefinition(tag);
        mapper.addDefinitionsToGroups(Collections.singletonList(definition));

        assertThrows(IllegalArgumentException.class, () -> mapper.addDefinitionsToGroups(Collections.singletonList(definition)));
    }

    @Test
    public void registerRemoveRegisterDefinitionShouldAddDefinitionToGroup() {
        SubscriptionGroup group = mapper.getGroup(tag);
        DataTagDefinition definition = mapper.getDefinition(tag);

        mapper.addDefinitionsToGroups(Collections.singletonList(definition));
        mapper.removeTagFromGroup(tag);
        mapper.addDefinitionsToGroups(Collections.singletonList(definition));

        assertTrue(group.contains(definition));
    }

    @Test
    public void registerDefinitionsInGroupShouldAddAllDefinitionsToGroup() {
        SubscriptionGroup group = mapper.getGroup(tag);
        DataTagDefinition definition = mapper.getDefinition(tag);
        DataTagDefinition definitionWithSameDeadband = mapper.getDefinition(tagWithSameDeadband);

        mapper.addDefinitionsToGroups(Arrays.asList(definition, definitionWithSameDeadband));

        assertTrue(group.contains(definition));
        assertTrue(group.contains(definitionWithSameDeadband));
    }

    @Test
    public void getTagByRandomClientHandleShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> mapper.getTag(UInteger.valueOf(2)));
    }

    @Test
    public void getTagByClientHandleShouldReturnProperTag() {
        DataTagDefinition definition = mapper.getDefinition(tag);

        ISourceDataTag getTagBy = mapper.getTag(definition.getClientHandle());

        assertEquals(tag, getTagBy);
    }
}
