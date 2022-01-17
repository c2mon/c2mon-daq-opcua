/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.metrics.MetricProxy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubscriptionGroupTest extends MappingBase {

    SubscriptionGroup group;
    SubscriptionGroup groupWithDifferentDeadband;

    @BeforeEach
    public void setup() {
        super.setup();

        group = new SubscriptionGroup(tag.getTimeDeadband(), new MetricProxy(new SimpleMeterRegistry()));
        groupWithDifferentDeadband = new SubscriptionGroup(tagWithDifferentDeadband.getTimeDeadband(), new MetricProxy(new SimpleMeterRegistry()));
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
