package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EndpointWriteToTagsTest extends EndpointTestBase {

    SourceCommandTag tag;
    SourceCommandTagValue value;
    OPCHardwareAddressImpl address;

    @BeforeEach
    public void setupCommandTagAndValue() {
        tag = new SourceCommandTag(0L, "Power");
        value = new SourceCommandTagValue();

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        value.setValue(1);
        value.setDataType("Integer");
    }

    @Test
    public void executeClassicCommandWithoutPulseShouldNotifyListener() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        CompletableFuture<?> writeResponse = ServerTestListener.createListenerAndReturnFutures(publisher).get(COMMAND_RESPONSE);
        endpoint.executeCommand(tag, value);
        assertEquals(StatusCode.GOOD, writeResponse.get(3000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void executeCommandWithBadValueShouldThrowException() {
        value.setDataType("Invalid");
        assertThrows(ConfigurationException.class,
                () -> endpoint.executeCommand(tag, value),
                ConfigurationException.Cause.COMMAND_VALUE_ERROR.message);
    }

    @Test
    public void writeBadClientShouldThrowException() {
        endpoint.setWrapper(new MiloExceptionTestClientWrapper());
        assertThrows(OPCCommunicationException.class,
                () -> endpoint.executeCommand(tag, value),
                OPCCommunicationException.Cause.WRITE.message);
        //clean up
        endpoint.setWrapper(client);
    }

    @Test
    public void executeMethodShouldNotifyListener() throws ConfigurationException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<?> methodFuture = ServerTestListener.createListenerAndReturnFutures(publisher).get(METHOD_RESPONSE);
        address.setOpcRedundantItemName("method");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        endpoint.executeCommand(tag, value);
        final Map.Entry<StatusCode, ISourceCommandTag> methodResponse = (Map.Entry<StatusCode, ISourceCommandTag>)methodFuture.get(3000, TimeUnit.MILLISECONDS);
        assertEquals(StatusCode.GOOD, methodResponse.getKey());
    }
    @Test
    public void executeMethodWithInvalidValueShouldThrowConfigException() {
        value.setDataType("invalid");
        assertThrows(ConfigurationException.class, () ->  endpoint.executeCommand(tag, value));
    }

    @Test
    public void executeMethodWithoutMethodAddressShouldNotifyListener() throws ConfigurationException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<?> methodFuture = ServerTestListener.createListenerAndReturnFutures(publisher).get(METHOD_RESPONSE);
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        endpoint.executeCommand(tag, value);
        final Map.Entry<StatusCode, ISourceCommandTag> methodResponse = (Map.Entry<StatusCode, ISourceCommandTag>)methodFuture.get(3000, TimeUnit.MILLISECONDS);
        assertEquals(StatusCode.GOOD, methodResponse.getKey());
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() throws ExecutionException, InterruptedException {
        CompletableFuture<?> aliveResponse = ServerTestListener.createListenerAndReturnFutures(publisher).get(ALIVE);
        endpoint.writeAlive(address, value);
        assertEquals(StatusCode.GOOD, aliveResponse.get());
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() {
        final MiloTestClientWrapper wrapper = new MiloExceptionTestClientWrapper();
        endpoint.setWrapper(wrapper);
        CompletableFuture<?> aliveResponse = ServerTestListener.createListenerAndReturnFutures(publisher).get(ALIVE);
        try{
            endpoint.writeAlive(address, value);
        } catch (OPCCommunicationException e) {
            // expected behavior
        }
        assertThrows(TimeoutException.class, () -> aliveResponse.get(3000, TimeUnit.MILLISECONDS));
    }

}