package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState.*;
import static org.junit.Assert.assertEquals;

public class EndpointEquipmentStateEventsTest extends EndpointTestBase {

    CompletableFuture<Object> future = new CompletableFuture<>();

    @BeforeEach
    public void setup() {
        super.setup();
        listenForEquipmentState();
    }

    @Test
    public void goodStatusCodesShouldSendOK () throws ExecutionException, InterruptedException {
        mockStatusCode(StatusCode.GOOD, tag1);
        endpoint.initialize(false);
        assertEquals(Collections.singletonList(OK), future.get());
    }

    @Test
    public void badStatusCodesShouldSendOK() throws ExecutionException, InterruptedException {
        mockStatusCode(StatusCode.BAD, tag1);
        endpoint.initialize(false);
        assertEquals(Collections.singletonList(OK), future.get());
    }

    @Test
    public void initializeAfterLostConnectionShouldSendLostAndOK () throws ExecutionException, InterruptedException {
        mockStatusCode(StatusCode.BAD, tag1);
        endpoint.initialize(true);
        assertEquals(Arrays.asList(CONNECTION_LOST, OK), future.get());
    }

    @Test
    public void errorOnInitializeShouldSendFail () throws ExecutionException, InterruptedException {
        endpoint.setClient(new MiloExceptionTestClientWrapper());
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize(false));
        assertEquals(Collections.singletonList(CONNECTION_FAILED), future.get());
    }

    @Test
    public void errorOnInitializeAfterLostConnectionShouldSendBoth () throws ExecutionException, InterruptedException {
        endpoint.setClient(new MiloExceptionTestClientWrapper());
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize(true));
        assertEquals(Arrays.asList(CONNECTION_LOST, CONNECTION_FAILED), future.get());
    }

    private void listenForEquipmentState () {
        List<EquipmentState> states = new ArrayList<>();
        publisher.subscribeToEquipmentStateEvents(value -> {
            states.add(value);
            if (!value.equals(CONNECTION_LOST)) {
                future.complete(states);
            }
        });
    }
}
