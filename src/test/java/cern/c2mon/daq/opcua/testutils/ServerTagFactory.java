package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;

import java.util.concurrent.atomic.AtomicLong;

public enum ServerTagFactory {
    RandomUnsignedInt32,
    AlternatingBoolean,
    Invalid,
    DipData;

    static AtomicLong ID = new AtomicLong();
    static int NAMESPACE = 2; // Hardcoded on the server

    public ISourceDataTag createDataTag() {
        return createDataTag(0, (short) 0, 0);
    }

    public ISourceDataTag createDataTag(float valueDeadband, short deadbandType, int timeDeadband) {
        OPCHardwareAddressImpl hardwareAddress = new OPCHardwareAddressImpl(this.name());
        hardwareAddress.setNamespace(NAMESPACE);

        DataTagAddress dataTagAddress = new DataTagAddress(hardwareAddress,
                0,
                deadbandType,
                valueDeadband,
                timeDeadband,
                2,
                true);

        return new SourceDataTag(ID.getAndIncrement(),
                this.name(),
                false,
                (short) 0,
                null,
                dataTagAddress);
    }
}
