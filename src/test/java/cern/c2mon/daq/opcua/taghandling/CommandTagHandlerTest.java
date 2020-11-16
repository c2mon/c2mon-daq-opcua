/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.metrics.MetricProxy;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.DIPHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class CommandTagHandlerTest {
    TestEndpoint endpoint;
    CommandTagHandler commandTagHandler;

    SourceCommandTagValue value;
    SourceCommandTag tag;
    OPCHardwareAddressImpl address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
    OPCHardwareAddressImpl pulseAddress;
    MessageSender l = new TestListeners.TestListener();

    @BeforeEach
    public void setUp() {
        this.endpoint = new TestEndpoint(l, new TagSubscriptionMapper(new MetricProxy(new SimpleMeterRegistry())));
        endpoint.setReturnGoodStatusCodes(true);
        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        pulseAddress = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw", 1);
        pulseAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);

        value = new SourceCommandTagValue();
        value.setDataType(Integer.class.getName());
        value.setValue(1);
        final Controller controllerProxy = TestUtils.getFailoverProxy(endpoint, l);
        commandTagHandler = new CommandTagHandler(controllerProxy);
    }

    @Test
    public void runCommandWithBadHardwareAddressShouldThrowException() {
        final HardwareAddress dip = new DIPHardwareAddressImpl("bad address type");
        tag.setHardwareAddress(dip);
        assertThrows(EqCommandTagException.class,
                () -> commandTagHandler.runCommand(tag, value),
                ExceptionContext.HARDWARE_ADDRESS_TYPE.getMessage());
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
        final Endpoint mockEp = mockWriteRewrite();
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        commandTagHandler.runCommand(tag, value);
        verify(mockEp);
    }

    @Test
    public void commandWithPulseShouldThrowExceptionWhenReadingNullValue() throws OPCUAException {
        value.setValue(0);
        tag.setHardwareAddress(pulseAddress);
        final Endpoint mockEp = EasyMock.niceMock(Endpoint.class);
        Map.Entry<ValueUpdate, SourceDataTagQuality> e = new AbstractMap.SimpleEntry<>(null, null);
        expect(mockEp.read(anyObject())).andReturn(e).anyTimes();
        replay(mockEp);
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        assertThrows(EqCommandTagException.class, () -> commandTagHandler.runCommand(tag, value));
    }

    @Test
    public void commandWithPulseShouldThrowExceptionOnInvalidQuality() throws OPCUAException {
        value.setValue(0);
        tag.setHardwareAddress(pulseAddress);
        final Endpoint mockEp = EasyMock.niceMock(Endpoint.class);
        Map.Entry<ValueUpdate, SourceDataTagQuality> e = new AbstractMap.SimpleEntry<>(new ValueUpdate(2), new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE));
        expect(mockEp.read(anyObject())).andReturn(e).anyTimes();
        replay(mockEp);
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        assertThrows(EqCommandTagException.class, () -> commandTagHandler.runCommand(tag, value));
    }

    @Test
    public void commandWithPulseShouldDoNothingIfAlreadySet() throws OPCUAException, EqCommandTagException {
        value.setValue(0);
        tag.setHardwareAddress(pulseAddress);
        final NodeId def = ItemDefinition.of(tag).getNodeId();
        final Endpoint mockEp = EasyMock.niceMock(Endpoint.class);
        expect(mockEp.read(anyObject())).andReturn(endpoint.read(def)).anyTimes();
        replay(mockEp);
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        commandTagHandler.runCommand(tag, value);
        verify(mockEp);
    }

    @Test
    public void interruptingPulseShouldStillResetTheTag() throws OPCUAException, InterruptedException {
        pulseAddress = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw", 5);
        tag.setHardwareAddress(pulseAddress);
        final Endpoint mockEp = mockWriteRewrite();
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));

        ExecutorService s = Executors.newFixedThreadPool(2);
        s.submit(() -> commandTagHandler.runCommand(tag, value));
        s.shutdownNow();
        s.awaitTermination(5, TimeUnit.SECONDS);
        verify(mockEp);
    }

    @Test
    public void interruptingPulseShouldTerminateEarly() throws OPCUAException, InterruptedException {
        pulseAddress = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw", 60);
        tag.setHardwareAddress(pulseAddress);
        final Endpoint mockEp = mockWriteRewrite();
        ExecutorService s = Executors.newFixedThreadPool(2);
        s.submit(() -> commandTagHandler.runCommand(tag, value));
        s.shutdownNow();
        s.awaitTermination(2, TimeUnit.SECONDS);
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
        endpoint.setThrowExceptions(true);
        verifyCommunicationException(ExceptionContext.WRITE);
    }

    @Test
    public void onAddCommandTagShouldReportSuccess() {
        assertChangeReportSuccess(r -> commandTagHandler.onAddCommandTag(null, r));
    }
    @Test
    public void onRemoveCommandTagShouldReportSuccess() {
        assertChangeReportSuccess(r -> commandTagHandler.onRemoveCommandTag(null, r));
    }

    @Test
    public void onUpdateCommandTagShouldReportSuccess() {
        assertChangeReportSuccess(r -> commandTagHandler.onUpdateCommandTag(null, null, r));
    }

    private void assertChangeReportSuccess(Consumer<ChangeReport> r) {
        final ChangeReport changeReport = new ChangeReport();
        r.accept(changeReport);
        assertTrue(changeReport.isSuccess());

    }

    private Endpoint mockWriteRewrite() throws OPCUAException {
        final Endpoint mockEp = EasyMock.niceMock(Endpoint.class);
        final NodeId def = ItemDefinition.of(tag).getNodeId();
        expect(mockEp.read(anyObject())).andReturn(endpoint.read(def)).anyTimes();
        expect(mockEp.write(def, 1)).andReturn(endpoint.write(def, 1)).once();
        expect(mockEp.write(def, 0)).andReturn(endpoint.write(def, 0)).once();
        replay(mockEp);
        ReflectionTestUtils.setField(commandTagHandler, "controller", TestUtils.getFailoverProxy(mockEp, l));
        return mockEp;
    }

    private void verifyCommunicationException(ExceptionContext context) {
        final EqCommandTagException e = assertThrows(EqCommandTagException.class,
                () -> commandTagHandler.runCommand(tag, value));
        assertTrue(e.getCause() instanceof CommunicationException);
        assertEquals(context.getMessage(), e.getCause().getMessage());
    }

}
