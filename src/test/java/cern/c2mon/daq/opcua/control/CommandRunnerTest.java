package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CommandRunnerTest {
    TestEndpoint endpoint;
    CommandRunner commandRunner;


    SourceCommandTagValue value;
    SourceCommandTag tag;
    OPCHardwareAddressImpl address;

    @BeforeEach
    public void setUp() {
        this.endpoint = new TestEndpoint();
        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        value = new SourceCommandTagValue();
        value.setDataType(Integer.class.getName());
        value.setValue(1);
        commandRunner = new CommandRunner(endpoint);
    }

    @Test
    public void runCommandWithInvalidValueShouldThrowConfigException() {
        value.setDataType("invalid");
        assertThrows(ConfigurationException.class,
                () ->  commandRunner.runCommand(tag, value),
                ConfigurationException.Cause.COMMAND_VALUE_ERROR.message);
    }

    @Test
    public void runMethodShouldReturnMethodValuesAsString() throws ConfigurationException {
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = commandRunner.runCommand(tag, value);
        assertEquals("1", s);
    }

    @Test
    public void executeCommandShouldThrowErrorOnWrongStatusCode() {
        endpoint.setReturnGoodStatusCodes(false);
        assertThrows(OPCCommunicationException.class, () -> commandRunner.executeCommand(tag, value), OPCCommunicationException.Context.WRITE.message);
    }

    @Test
    public void writeBadClientShouldThrowException() {
        commandRunner.setEndpoint(new ExceptionTestEndpoint());
        assertThrows(OPCCommunicationException.class,
                () -> commandRunner.executeCommand(tag, value),
                OPCCommunicationException.Context.WRITE.message);
    }

    @Test
    public void executeMethodWithMethodAddress() {
        address.setOpcRedundantItemName("method");
        final Object[] output = commandRunner.executeMethod(tag, 1);
        assertEquals(1, output.length);
        assertEquals(1, output[0]);
    }

    @Test
    public void executeMethodWithoutMethodAddressShouldReturnValue() {
        final Object[] output = commandRunner.executeMethod(tag, 1);
        assertEquals(1, output.length);
        assertEquals(1, output[0]);
    }

    @Test
    public void commandWithPulseShouldNotDoAnythingIfAlreadySet() {
        endpoint.setReturnGoodStatusCodes(false);
        assertDoesNotThrow(() -> commandRunner.executePulseCommand(tag, 0, 2));
    }

    @Test
    public void commandWithPulseShouldThrowExceptionOnBadStatusCode() {
        endpoint.setReturnGoodStatusCodes(false);
        assertThrows(OPCCommunicationException.class,
                () -> commandRunner.executePulseCommand(tag, 1, 2),
                OPCCommunicationException.Context.WRITE.message);
    }

    @Test
    public void commandShouldThrowExceptionOnBadStatusCode() {
        endpoint.setReturnGoodStatusCodes(false);
        assertThrows(OPCCommunicationException.class,
                () -> commandRunner.executeCommand(tag, 1),
                OPCCommunicationException.Context.WRITE.message);
    }

}
