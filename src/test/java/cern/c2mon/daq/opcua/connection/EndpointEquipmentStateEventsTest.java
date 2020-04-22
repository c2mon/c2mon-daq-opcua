package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EndpointListener.EquipmentState;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
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

import static cern.c2mon.daq.opcua.upstream.EndpointListener.EquipmentState.*;
import static org.junit.Assert.assertEquals;

public class EndpointEquipmentStateEventsTest extends EndpointTestBase {

    CompletableFuture<Object> future = new CompletableFuture<>();

    @BeforeEach
    public void setup () {
        super.setup();
        listenForEquipmentState();
    }

    @Test
    public void goodStatusCodesShouldSendOK () throws ExecutionException, InterruptedException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag1);
        endpoint.initialize(false);
        assertEquals(Collections.singletonList(OK), future.get());
    }

    @Test
    public void badStatusCodesShouldSendOK () throws ExecutionException, InterruptedException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.BAD, tag1);
        endpoint.initialize(false);
        assertEquals(Collections.singletonList(OK), future.get());
    }

    @Test
    public void initializeAfterLostConnectionShouldSendLostAndOK () throws ExecutionException, InterruptedException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.BAD, tag1);
        endpoint.initialize(true);
        assertEquals(Arrays.asList(CONNECTION_LOST, OK), future.get());
    }

    @Test
    public void errorOnInitializeShouldSendFail () throws ExecutionException, InterruptedException {
        endpoint.setWrapper(new MiloExceptionTestClientWrapper());
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize("uri"));
        assertEquals(Collections.singletonList(CONNECTION_FAILED), future.get());
    }

    @Test
    public void errorOnInitializeAfterLostConnectionShouldSendBoth () throws ExecutionException, InterruptedException {
        endpoint.setWrapper(new MiloExceptionTestClientWrapper());
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize(true));
        assertEquals(Arrays.asList(CONNECTION_LOST, CONNECTION_FAILED), future.get());
    }

    private void listenForEquipmentState () {
        List<EquipmentState> states = new ArrayList<>();
        publisher.subscribe(new EndpointListener() {
            @Override
            public void update (EquipmentState state) {
                states.add(state);
                if (!state.equals(CONNECTION_LOST)) {
                    future.complete(states);
                }
            }

            @Override
            public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {

            }

            @Override
            public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {

            }

            @Override
            public void onWriteResponse(StatusCode statusCode, ISourceCommandTag tag) {

            }

            @Override
            public void initialize (IEquipmentMessageSender sender) {

            }
        });
    }
}
