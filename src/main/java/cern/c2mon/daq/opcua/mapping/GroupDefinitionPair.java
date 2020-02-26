package cern.c2mon.daq.opcua.mapping;

import lombok.Data;

@Data(staticConstructor = "of")
public class GroupDefinitionPair {
    private final SubscriptionGroup subscriptionGroup;
    private final ItemDefinition itemDefinition;
}