package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ControllerTest extends ControllerTestBase {

    SourceCommandTagValue value;
    SourceCommandTag tag;
    OPCHardwareAddressImpl address;

    @BeforeEach
    public void setupMocker() throws ConfigurationException {
        mocker.replay();
        controller.initialize(config);

        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        value = new SourceCommandTagValue();
        value.setDataType(Integer.class.getName());
        value.setValue(1);
    }

    @Test
    public void initializeShouldSubscribeTags() {
        sourceTags.values().forEach(dataTag -> Assertions.assertTrue(mapper.getGroup(dataTag).isSubscribed()));
    }

    @Test
    public void stopShouldResetEndpoint() {
        controller.stop();
        Assertions.assertTrue(mapper.getTagIdDefinitionMap().isEmpty());
    }


    @Test
    public void runCommandWithInvalidValueShouldThrowConfigException() {
        value.setDataType("invalid");
        assertThrows(ConfigurationException.class,
                () ->  controller.runCommand(tag, value),
                ConfigurationException.Cause.COMMAND_VALUE_ERROR.message);
    }

    @Test
    public void runMethodShouldReturnMethodValuesAsString() throws ConfigurationException {
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = controller.runCommand(tag, value);
        assertEquals("1", s);
    }
}
