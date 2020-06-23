package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class CommandRunnerTest {
    TestEndpoint endpoint;
    CommandRunner commandRunner;


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
        final FailoverProxy failoverProxy = TestUtils.getFailoverProxy(endpoint);
        commandRunner = new CommandRunner(failoverProxy);
    }

    @Test
    public void runCommandWithInvalidValueShouldThrowConfigInEqException() {
        value.setDataType("invalid");
        assertThrows(EqCommandTagException.class,
                () -> commandRunner.runCommand(tag, value),
                ExceptionContext.COMMAND_VALUE_ERROR.getMessage());
    }

    @Test
    public void runMethodWithoutMethodAddressShouldReturnMethodValuesAsString() throws EqCommandTagException {
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = commandRunner.runCommand(tag, value);
        assertEquals("1", s);
    }

    @Test
    public void runMethodWithMethodAddressShouldReturnMethodValuesAsString() throws EqCommandTagException {
        address.setOpcRedundantItemName("method");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = commandRunner.runCommand(tag, value);
        assertEquals("1", s);
    }


    @Test
    public void commandWithPulseShouldNotDoAnythingIfAlreadySet() throws EqCommandTagException, OPCUAException, InterruptedException {
        tag.setHardwareAddress(pulseAddress);
        value.setValue(0);
        final Endpoint mockEp = EasyMock.mock(Endpoint.class);
        expect(mockEp.read(anyObject()))
                .andReturn(endpoint.read(ItemDefinition.toNodeId(tag)))
                .anyTimes();
        mockEp.initialize(anyString(), anyObject());
        EasyMock.expectLastCall().anyTimes();
        //no call to write
        replay(mockEp);
        commandRunner.setFailoverProxy(TestUtils.getFailoverProxy(mockEp));
        commandRunner.runCommand(tag, value);
        verify(mockEp);
    }

    @Test
    public void commandWithPulseShouldReadSetReset() throws EqCommandTagException, OPCUAException, InterruptedException {
        tag.setHardwareAddress(pulseAddress);
        final NodeId def = ItemDefinition.toNodeId(tag);
        final Endpoint mockEp = EasyMock.mock(Endpoint.class);
        mockEp.initialize(anyString(), anyObject());
        EasyMock.expectLastCall().anyTimes();
        expect(mockEp.read(anyObject())).andReturn(endpoint.read(def)).anyTimes();
        expect(mockEp.write(def, 1)).andReturn(endpoint.write(def, 1)).once();
        expect(mockEp.write(def, 0)).andReturn(endpoint.write(def, 0)).once();
        replay(mockEp);
        commandRunner.setFailoverProxy(TestUtils.getFailoverProxy(mockEp));
        commandRunner.runCommand(tag, value);
        verify(mockEp);
    }

    @Test
    public void commandWithPulseShouldThrowExceptionOnBadStatusCode() {
        tag.setHardwareAddress(pulseAddress);
        endpoint.setReturnGoodStatusCodes(false);
        //The error should already be thrown at READ as the first action of the pulse command
        verifyCommunicationException(ExceptionContext.READ);
    }

    @Test
    public void commandWithoutPulseShouldThrowExceptionOnBadStatusCode() {
        endpoint.setReturnGoodStatusCodes(false);
        verifyCommunicationException(ExceptionContext.COMMAND_CLASSIC);
    }

    @Test
    public void methodShouldThrowErrorOnWrongStatusCode() {
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        endpoint.setReturnGoodStatusCodes(false);
        verifyCommunicationException(ExceptionContext.METHOD_CODE);
    }

    @Test
    public void badClientShouldThrowException() {
        commandRunner.setFailoverProxy(TestUtils.getFailoverProxy(new ExceptionTestEndpoint()));
        verifyCommunicationException(ExceptionContext.WRITE);
    }

    private void verifyCommunicationException(ExceptionContext context) {
        final EqCommandTagException e = assertThrows(EqCommandTagException.class,
                () -> commandRunner.runCommand(tag, value));
        assertTrue(e.getCause() instanceof CommunicationException);
        assertEquals(context.getMessage(), e.getCause().getMessage());
    }

}
