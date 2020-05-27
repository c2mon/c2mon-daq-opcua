package cern.c2mon.daq.opcua.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    public void addTagWithBadDeadbandShouldThrowError() {
        group.add(tagWithDifferentDeadband.getId());
        assertThrows(IllegalArgumentException.class, () -> group.add(tagWithDifferentDeadband.getId()));
    }

    @Test
    public void addDefinitionTwiceShouldThrowError() {
        group.add(tag.getId());

        assertThrows(IllegalArgumentException.class, () -> group.add(tag.getId()));
    }

    @Test
    public void removeDefinitionShouldRemoveDefinitionFromGroup() {
        group.add(tag.getId());
        group.remove(tag);

        assertFalse(group.contains(tag));
    }
}
