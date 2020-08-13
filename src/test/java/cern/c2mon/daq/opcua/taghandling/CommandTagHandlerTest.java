package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.NoFailover;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.*;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class CommandTagHandlerTest {
    TestEndpoint endpoint;
    CommandTagHandler commandTagHandler;

    SourceCommandTagValue value;
    SourceCommandTag tag;
    OPCHardwareAddressImpl address;
    OPCHardwareAddressImpl pulseAddress;
    MessageSender l = new TestListeners.TestListener();

    @BeforeEach
    public void setUp() {
        this.endpoint = new TestEndpoint(l, new TagSubscriptionMapper());
        endpoint.setReturnGoodStatusCodes(true);
        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        pulseAddress = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw", 2);
        pulseAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);

        value = new SourceCommandTagValue();
        value.setDataType(Integer.class.getName());
        value.setValue(1);
        final Controller controllerProxy = TestUtils.getFailoverProxy(endpoint, l);
        commandTagHandler = new CommandTagHandler(controllerProxy);
    }

    @Test
    public void runCommandWithInvalidValueShouldThrowConfigInEqException() {
        value.setDataType("invalid");
        assertThrows(EqCommandTagException.class,
                () -> commandTagHandler.runCommand(tag, value),
                ExceptionContext.COMMAND_VALUE_ERROR.getMessage());
    }

    @Test
    public void runMethodWithoutMethodAddressShouldReturnMethodValuesAsString() throws EqCommandTagException {
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = commandTagHandler.runCommand(tag, value);
        assertEquals("1", s);
    }

    @Test
    public void runMethodWithMethodAddressShouldReturnMethodValuesAsString() throws EqCommandTagException {
        address.setOpcRedundantItemName("method");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final String s = commandTagHandler.runCommand(tag, value);
        assertEquals("1", s);
    }


    @Test
    public void commandWithPulseShouldNotDoAnythingIfAlreadySet() throws EqCommandTagException, OPCUAException {
        tag.setHardwareAddress(pulseAddress);
        value.setValue(0);
        final Endpoint mockEp = EasyMock.niceMock(Endpoint.class);
        final ItemDefinition definition = ItemDefinition.of(tag);
        expect(mockEp.read(anyObject()))
                .andReturn(endpoint.read(definition.getNodeId()))
                .anyTimes();
        //no call to write
        replay(mockEp);
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        commandTagHandler.runCommand(tag, value);
        verify(mockEp);
    }

    @Test
    public void commandWithPulseShouldReadSetReset() throws EqCommandTagException, OPCUAException {
        tag.setHardwareAddress(pulseAddress);
        final NodeId def = ItemDefinition.of(tag).getNodeId();
        final Endpoint mockEp = EasyMock.niceMock(Endpoint.class);
        expect(mockEp.read(anyObject())).andReturn(endpoint.read(def)).anyTimes();
        expect(mockEp.write(def, 1)).andReturn(endpoint.write(def, 1)).once();
        expect(mockEp.write(def, 0)).andReturn(endpoint.write(def, 0)).once();
        replay(mockEp);
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        commandTagHandler.runCommand(tag, value);
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
    public void badClientShouldThrowException() throws OPCUAException {
        ConcreteController controller = new NoFailover();
        controller.initialize(new ExceptionTestEndpoint(l, new TagSubscriptionMapper()));
        final TestControllerProxy proxy = TestUtils.getFailoverProxy(endpoint, l);
        proxy.setController(controller);
        ReflectionTestUtils.setField(commandTagHandler, "controller", proxy);
        verifyCommunicationException(ExceptionContext.WRITE);
    }

    private void verifyCommunicationException(ExceptionContext context) {
        final EqCommandTagException e = assertThrows(EqCommandTagException.class,
                () -> commandTagHandler.runCommand(tag, value));
        assertTrue(e.getCause() instanceof CommunicationException);
        assertEquals(context.getMessage(), e.getCause().getMessage());
    }

}
