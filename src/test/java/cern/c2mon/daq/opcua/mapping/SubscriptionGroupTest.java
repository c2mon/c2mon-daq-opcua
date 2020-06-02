package cern.c2mon.daq.opcua.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubscriptionGroupTest extends MappingBase {

    SubscriptionGroup group;
    SubscriptionGroup groupWithDifferentDeadband;

    @BeforeEach
    public void setup() {
        super.setup();

        group = new SubscriptionGroup(tag.getTimeDeadband());
        groupWithDifferentDeadband = new SubscriptionGroup(tagWithDifferentDeadband.getTimeDeadband());
    }

    @Test
    public void addDefinitionTwiceShouldNotAddAnything() {
        group.add(tag.getId());
        group.add(tag.getId());
        assertEquals(1, group.size());
    }

    @Test
    public void removeDefinitionShouldRemoveDefinitionFromGroup() {
        group.add(tag.getId());
        group.remove(tag);

        assertFalse(group.contains(tag));
    }
}