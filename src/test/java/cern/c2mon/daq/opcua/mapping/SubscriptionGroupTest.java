package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
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
    public void addDefinitionTwiceShouldNotAddAnything() throws ConfigurationException {
        group.add(tag.getId(), ItemDefinition.of(tag));
        group.add(tag.getId(), ItemDefinition.of(tag));
        assertEquals(1, group.size());
    }

    @Test
    public void removeDefinitionShouldRemoveDefinitionFromGroup() throws ConfigurationException {
        group.add(tag.getId(), ItemDefinition.of(tag));
        group.remove(tag.getId());

        assertFalse(group.contains(tag.getId()));
    }
}
