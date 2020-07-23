package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class MappingBase {


    OPCHardwareAddressImpl opcHardwareAddress;
    DataTagAddress dataTagAddress;
    DataTagAddress dataTagAddressWithDifferentDeadband;
    ISourceDataTag tag;
    ISourceDataTag tagWithSameDeadband;
    ISourceDataTag tagWithDifferentDeadband;

    TagSubscriptionMapper mapper = new TagSubscriptionMapper();


    @BeforeEach
    public void setup() {
        mapper.clear();
        opcHardwareAddress = new OPCHardwareAddressImpl("Primary");
        dataTagAddress = new DataTagAddress(opcHardwareAddress,
                0, (short) 0,0, 0, 2, true);
        dataTagAddressWithDifferentDeadband = new DataTagAddress(opcHardwareAddress,
                0, (short) 1,1, 1, 2, true);
        tag = makeSourceDataTag(1L, dataTagAddress);
        tagWithSameDeadband = makeSourceDataTag(2L, dataTagAddress);
        tagWithDifferentDeadband = makeSourceDataTag(3L, dataTagAddressWithDifferentDeadband);
    }


    @NotNull
    protected SourceDataTag makeSourceDataTag(long id, DataTagAddress tagAddress) {
        return new SourceDataTag(id, "Primary", false, (short) 0, null, tagAddress);
    }
}
