package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ControllerTest extends ControllerTestBase {

    SourceCommandTag tag;
    SourceCommandTagValue value;
    OPCHardwareAddressImpl address;


    @BeforeEach
    public void setUp () throws ConfigurationException, CommunicationException{
        super.setUp();
        tag = new SourceCommandTag(0L, "Power");

        address = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        address.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);
        tag.setHardwareAddress(address);

        endpoint.setReturnGoodStatusCodes(true);
    }

    @Test
    public void initializeShouldSubscribeTags() throws ConfigurationException, CommunicationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        controller.connect(uri);
        controller.subscribeTags(sourceTags.values());
        sourceTags.values().forEach(dataTag -> Assertions.assertTrue(mapper.getGroup(dataTag).isSubscribed()));
    }

    @Test
    public void stopShouldResetEndpoint() throws CommunicationException {
        controller.stop();
        Assertions.assertTrue(mapper.getTagIdDefinitionMap().isEmpty());
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() throws ExecutionException, InterruptedException, TimeoutException, CommunicationException, ConfigurationException {
        controller.writeAlive(address, value);
        Assertions.assertEquals(StatusCode.GOOD, listener.getAlive().get(3000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() {
        controller.setEndpoint(new ExceptionTestEndpoint());
        try{
            controller.writeAlive(address, value);
        } catch (CommunicationException e) {
            // expected behavior
        }
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(3000, TimeUnit.MILLISECONDS));
    }


}