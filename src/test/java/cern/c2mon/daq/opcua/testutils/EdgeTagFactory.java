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
package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.util.JmsMessagePriority;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;

import java.util.concurrent.atomic.AtomicLong;

public enum EdgeTagFactory {
    RandomUnsignedInt32,
    AlternatingBoolean,
    Invalid,
    DipData,
    PositiveTrendData,
    StartStepUp;

    static AtomicLong ID = new AtomicLong();
    static int NAMESPACE = 2; // Hardcoded on the server

    public ISourceDataTag createDataTag() {
        return createDataTag(0, ValueDeadbandType.NONE, 0);
    }

    public ISourceDataTag createDataTagWithID(long id) {
        return createDataTagWithID(id, 0, ValueDeadbandType.NONE, 0);
    }

    public ISourceDataTag createDataTag(float valDB, ValueDeadbandType dbType, int timeDB) {
        return createDataTagWithID(ID.getAndIncrement(), valDB, dbType, timeDB);
    }

    public ISourceDataTag createDataTagWithID(long id, float valDB, ValueDeadbandType dbType, int timeDB) {
        OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl(this.name());
        hwAddress.setNamespace(NAMESPACE);
        DataTagAddress dataTagAddress = new DataTagAddress(hwAddress, 0, dbType, valDB, timeDB, JmsMessagePriority.PRIORITY_LOW, true);
        return new SourceDataTag(id, this.name(), false, (short) 0, null, dataTagAddress);
    }

    public ISourceCommandTag createMethodTag(boolean withParent) {
        OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl(this.name());
        if (withParent) {
            hwAddress = new OPCHardwareAddressImpl("Methods");
            hwAddress.setOpcRedundantItemName(this.name());
        }
        hwAddress.setNamespace(NAMESPACE);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        return new SourceCommandTag(ID.getAndIncrement(), this.name(), 10, 10, hwAddress);
    }
}
