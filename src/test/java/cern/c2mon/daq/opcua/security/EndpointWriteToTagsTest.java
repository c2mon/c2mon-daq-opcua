package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        CompletableFuture<Object> writeResponse = ServerTestListener.listenForWriteResponse(publisher);
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
    public void writeToValidAddressShouldReturnGoodStatusCode() {
        assertEquals(StatusCode.GOOD, endpoint.write(address, value));
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
}