package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
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
    ControlDelegate delegate;


    SourceCommandTagValue value;
    SourceCommandTag tag;
    OPCHardwareAddressImpl address;
    OPCHardwareAddressImpl pulseAddress;

    @BeforeEach
    public void setUp() {
        this.endpoint = new TestEndpoint();
        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        pulseAddress = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw", 2);
        pulseAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);

        value = new SourceCommandTagValue();
        value.setDataType(Integer.class.getName());
        value.setValue(1);
        commandRunner = new CommandRunner(endpoint);
        delegate = new ControlDelegate(TestUtils.createDefaultConfig(), null, commandRunner);
    }

    @Test
    public void runCommandWithInvalidValueShouldThrowConfigException() {
        value.setDataType("invalid");
        assertThrows(EqCommandTagException.class,
                () ->  delegate.runCommand(tag, value),
                ConfigurationException.Cause.COMMAND_VALUE_ERROR.message);
    }

    @Test
    public void runMethodShouldReturnMethodValuesAsString() throws EqCommandTagException {
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = delegate.runCommand(tag, value);
        assertEquals("1", s);
    }

    @Test
    public void commandWithPulseShouldNotDoAnythingIfAlreadySet() {
        tag.setHardwareAddress(pulseAddress);
        value.setValue(0);
        assertDoesNotThrow(() -> delegate.runCommand(tag, value));
    }

    @Test
    public void commandWithPulseShouldThrowExceptionOnBadStatusCode() {
        tag.setHardwareAddress(pulseAddress);
        endpoint.setReturnGoodStatusCodes(false);
        assertThrows(EqCommandTagException.class,
                () -> delegate.runCommand(tag, value),
                ExceptionContext.WRITE.getMessage());
    }

    @Test
    public void executeCommandShouldThrowErrorOnWrongStatusCode() {
        endpoint.setReturnGoodStatusCodes(false);
        assertThrows(CommunicationException.class,
                () -> commandRunner.executeCommand(tag, value),
                ExceptionContext.WRITE.getMessage());
    }

    @Test
    public void writeBadClientShouldThrowException() {
        commandRunner.setEndpoint(new ExceptionTestEndpoint());
        assertThrows(CommunicationException.class,
                () -> commandRunner.executeCommand(tag, value),
                ExceptionContext.WRITE.getMessage());
    }

    @Test
    public void executeMethodWithMethodAddress() throws CommunicationException, ConfigurationException {
        address.setOpcRedundantItemName("method");
        final Object[] output = commandRunner.executeMethod(tag, 1);
        assertEquals(1, output.length);
        assertEquals(1, output[0]);
    }

    @Test
    public void executeMethodWithoutMethodAddressShouldReturnValue() throws CommunicationException, ConfigurationException {
        final Object[] output = commandRunner.executeMethod(tag, 1);
        assertEquals(1, output.length);
        assertEquals(1, output[0]);
    }

    @Test
    public void commandShouldThrowExceptionOnBadStatusCode() {
        endpoint.setReturnGoodStatusCodes(false);
        assertThrows(CommunicationException.class,
                () -> commandRunner.executeCommand(tag, 1),
                ExceptionContext.WRITE.getMessage());
    }

}
