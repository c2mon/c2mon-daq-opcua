package cern.c2mon.daq.opcua.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SubscriptionGroupTest extends MappingBase {

    DataTagDefinition definition;
    DataTagDefinition definitionWithSameDeadband;
    DataTagDefinition definitionWithDifferentDeadband;

    SubscriptionGroup group;
    SubscriptionGroup groupWithDifferentDeadband;

    @BeforeEach
    public void setup() {
        super.setup();

        definition = mapper.getDefinition(tag);
        definitionWithDifferentDeadband = mapper.getDefinition(tagWithDifferentDeadband);
        definitionWithSameDeadband = mapper.getDefinition(tagWithSameDeadband);

        group = new SubscriptionGroup(tag.getTimeDeadband());
        groupWithDifferentDeadband = new SubscriptionGroup(tagWithDifferentDeadband.getTimeDeadband());
    }

    @Test
    public void addDefinitionWithBadDeadbandShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> group.add(definitionWithDifferentDeadband));
    }

    @Test
    public void addDefinitionTwiceShouldThrowError() {
        group.add(definition);

        assertThrows(IllegalArgumentException.class, () -> group.add(definition));
    }

    @Test
    public void removeDefinitionShouldRemoveDefinitionFromGroup() {
        group.add(definition);
        group.remove(definition);

        assertFalse(group.contains(definition));
    }


}
