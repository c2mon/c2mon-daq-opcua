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
        assertThrows(IllegalArgumentException.class, () -> group.add(tagWithDifferentDeadband));
    }

    @Test
    public void addDefinitionTwiceShouldThrowError() {
        group.add(tag);

        assertThrows(IllegalArgumentException.class, () -> group.add(tag));
    }

    @Test
    public void removeDefinitionShouldRemoveDefinitionFromGroup() {
        group.add(tag);
        group.remove(tag);

        assertFalse(group.contains(tag));
    }


}
