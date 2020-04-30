package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointWriteToTagsTest extends EndpointTestBase {

    SourceCommandTag tag;
    SourceCommandTagValue value;
    OPCHardwareAddressImpl address;
    ServerTestListener.TestListener listener;

    @BeforeEach
    public void setupCommandTagAndValue() {
        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        listener = ServerTestListener.subscribeAndReturnListener(publisher);
        client.setReturnGoodStatusCodes(true);
        endpoint.setWrapper(client);
    }

    @Test
    public void executeCommandShouldThrowErrorOnWrongStatusCode() {
        client.setReturnGoodStatusCodes(false);
        assertThrows(OPCCommunicationException.class, () -> endpoint.executeCommand(tag, value), OPCCommunicationException.Context.WRITE.message);
    }


    @Test
    public void writeBadClientShouldThrowException() {
        endpoint.setWrapper(new MiloExceptionTestClientWrapper());
        assertThrows(OPCCommunicationException.class,
                () -> endpoint.executeCommand(tag, value),
                OPCCommunicationException.Context.WRITE.message);
    }

    @Test
    public void executeMethodWithMethodAddress() {
        address.setOpcRedundantItemName("method");
        final Object[] output = endpoint.executeMethod(tag, 1);
        assertEquals(1, output.length);
        assertEquals(1, output[0]);
    }

    @Test
    public void executeMethodWithoutMethodAddressShouldReturnValue() {
        final Object[] output = endpoint.executeMethod(tag, 1);
        assertEquals(1, output.length);
        assertEquals(1, output[0]);
    }

    @Test
    public void commandWithPulseShouldNotDoAnythingIfAlreadySet() {
        client.setReturnGoodStatusCodes(false);
        assertDoesNotThrow(() -> endpoint.executePulseCommand(tag, 0, 2));
    }

    @Test
    public void commandWithPulseShouldThrowExceptionOnBadStatusCode() {
        client.setReturnGoodStatusCodes(false);
        assertThrows(OPCCommunicationException.class,
                () -> endpoint.executePulseCommand(tag, 1, 2),
                OPCCommunicationException.Context.WRITE.message);
    }

    @Test
    public void commandShouldThrowExceptionOnBadStatusCode() {
        client.setReturnGoodStatusCodes(false);
        assertThrows(OPCCommunicationException.class,
                () -> endpoint.executeCommand(tag, 1),
                OPCCommunicationException.Context.WRITE.message);
    }


    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() throws ExecutionException, InterruptedException, TimeoutException {
        endpoint.writeAlive(address, value);
        assertEquals(StatusCode.GOOD, listener.getAlive().get(3000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() {
        final MiloTestClientWrapper wrapper = new MiloExceptionTestClientWrapper();
        endpoint.setWrapper(wrapper);
        try{
            endpoint.writeAlive(address, value);
        } catch (OPCCommunicationException e) {
            // expected behavior
        }
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(3000, TimeUnit.MILLISECONDS));
    }

}