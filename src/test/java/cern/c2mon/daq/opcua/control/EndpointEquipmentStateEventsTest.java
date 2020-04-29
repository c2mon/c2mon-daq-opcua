package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.*;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EndpointEquipmentStateEventsTest extends EndpointTestBase {

    CompletableFuture<List<EndpointListener.EquipmentState>> f;

    @BeforeEach
    public void setup () {
        super.setup();
        f = ServerTestListener.subscribeAndReturnListener(publisher).getStateUpdate();
    }

    @Test
    public void goodStatusCodesShouldSendOK () throws ExecutionException, InterruptedException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag1);
        endpoint.connect(false);
        assertEquals(Collections.singletonList(OK), f.get());
    }

    @Test
    public void badStatusCodesShouldSendOK () throws ExecutionException, InterruptedException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.BAD, tag1);
        endpoint.connect(false);
        assertEquals(Collections.singletonList(OK), f.get());
    }

    @Test
    public void initializeAfterLostConnectionShouldSendLostAndOK () throws ExecutionException, InterruptedException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.BAD, tag1);
        endpoint.connect(true);
        assertEquals(Arrays.asList(CONNECTION_LOST, OK), f.get());
    }

    @Test
    public void errorOnInitializeShouldSendFail () throws ExecutionException, InterruptedException {
        endpoint.setWrapper(new MiloExceptionTestClientWrapper());
        endpoint.initialize("uri");
        assertThrows(OPCCommunicationException.class, () -> endpoint.connect(false));
        assertEquals(Collections.singletonList(CONNECTION_FAILED), f.get());
    }

    @Test
    public void errorOnInitializeAfterLostConnectionShouldSendBoth () throws ExecutionException, InterruptedException {
        endpoint.setWrapper(new MiloExceptionTestClientWrapper());
        assertThrows(OPCCommunicationException.class, () -> endpoint.connect(true));
        assertEquals(Arrays.asList(CONNECTION_LOST, CONNECTION_FAILED), f.get());
    }
}
