/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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

import cern.c2mon.daq.opcua.metrics.MetricProxy;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.util.JmsMessagePriority;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class MappingBase {


    OPCHardwareAddressImpl opcHardwareAddress;
    DataTagAddress dataTagAddress;
    DataTagAddress dataTagAddressWithDifferentDeadband;
    ISourceDataTag tag;
    ISourceDataTag tagWithSameDeadband;
    ISourceDataTag tagWithDifferentDeadband;

    TagSubscriptionMapper mapper = new TagSubscriptionMapper(new MetricProxy(new SimpleMeterRegistry()));


    @BeforeEach
    public void setup() {
        mapper.clear();
        opcHardwareAddress = new OPCHardwareAddressImpl("Primary");
        dataTagAddress = new DataTagAddress(opcHardwareAddress,
                0, ValueDeadbandType.EQUIPMENT_ABSOLUTE,0, 0, JmsMessagePriority.PRIORITY_LOW, true);
        dataTagAddressWithDifferentDeadband = new DataTagAddress(opcHardwareAddress,
                0, ValueDeadbandType.EQUIPMENT_RELATIVE,1, 1, JmsMessagePriority.PRIORITY_LOW, true);
        tag = makeSourceDataTag(1L, dataTagAddress);
        tagWithSameDeadband = makeSourceDataTag(2L, dataTagAddress);
        tagWithDifferentDeadband = makeSourceDataTag(3L, dataTagAddressWithDifferentDeadband);
    }


    @NotNull
    protected SourceDataTag makeSourceDataTag(long id, DataTagAddress tagAddress) {
        return new SourceDataTag(id, "Primary", false, (short) 0, null, tagAddress);
    }
}
