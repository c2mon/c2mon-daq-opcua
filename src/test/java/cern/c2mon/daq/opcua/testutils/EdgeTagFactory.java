package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;

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
        return createDataTag(0, (short) 0, 0);
    }

    public ISourceDataTag createDataTagWithID(long id) {
        return createDataTagWithID(id, 0, (short) 0, 0);
    }

    public ISourceDataTag createDataTag(float valDB, short dbType, int timeDB) {
        return createDataTagWithID(ID.getAndIncrement(), valDB, dbType, timeDB);
    }

    public ISourceDataTag createDataTagWithID(long id, float valDB, short dbType, int timeDB) {
        OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl(this.name());
        hwAddress.setNamespace(NAMESPACE);
        DataTagAddress dataTagAddress = new DataTagAddress(hwAddress, 0, dbType, valDB, timeDB, 2, true);
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
