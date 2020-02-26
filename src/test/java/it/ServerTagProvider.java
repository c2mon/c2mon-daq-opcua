package it;

import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;

import java.util.concurrent.atomic.AtomicLong;

public enum ServerTagProvider {
    RandomUnsignedInt32,
    RandomBoolean,
    Invalid,
    DipData;

    static AtomicLong id = new AtomicLong();

    public ISourceDataTag createDataTag() {
        return createDataTag(0, (short) 0, 0);
    }

    public ISourceDataTag createDataTag(float valueDeadband, short deadbandType, int timeDeadband) {
        DataTagAddress dataTagAddress = new DataTagAddress(new OPCHardwareAddressImpl(this.name()),
                0, deadbandType, valueDeadband, timeDeadband, 2, true);
        return new SourceDataTag(id.getAndIncrement(), this.name(), false, (short) 0, null, dataTagAddress);
    }
}
