package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TagControllerTest extends TagControllerTestBase {

    SourceCommandTag tag;
    SourceCommandTagValue value;
    OPCHardwareAddressImpl address;


    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        super.setUp();
        tag = new SourceCommandTag(0L, "Power");
        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);
        endpoint.setReturnGoodStatusCodes(true);
    }

    @Test
    public void initializeShouldSubscribeTags() throws OPCUAException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        controller.subscribeTags(sourceTags.values());
        sourceTags.values().forEach(dataTag -> Assertions.assertTrue(mapper.getGroup(dataTag).contains(dataTag)));
    }

    @Test
    public void stopShouldResetEndpoint() {
        proxy.stop();
        Assertions.assertTrue(mapper.getTagIdDefinitionMap().isEmpty());
    }
}