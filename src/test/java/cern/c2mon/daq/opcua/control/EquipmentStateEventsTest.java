package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.daq.config.ChangeReport;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.*;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EquipmentStateEventsTest extends ControllerTestBase {

    CompletableFuture<List<EndpointListener.EquipmentState>> f;

    @BeforeEach
    public void setUp() {
        super.setUp();
        endpoint.setReturnGoodStatusCodes(true);
        f = ServerTestListener.subscribeAndReturnListener(publisher).getStateUpdate();
        endpoint.setConnected(true);
    }

    @Test
    public void goodStatusCodesShouldSendOK () throws ExecutionException, InterruptedException, ConfigurationException, TimeoutException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        controller.initialize(uri, sourceTags.values());
        assertEquals(Collections.singletonList(OK), f.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void badStatusCodesShouldSendOK () throws ExecutionException, InterruptedException, ConfigurationException, TimeoutException {
        endpoint.setReturnGoodStatusCodes(false);
        mocker.mockStatusCodeAndClientHandle(StatusCode.BAD, sourceTags.values());
        mocker.replay();
        controller.initialize(uri, sourceTags.values());
        assertEquals(Collections.singletonList(OK), f.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void reconnectShouldSendLostAndOK () throws ExecutionException, InterruptedException, TimeoutException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.BAD, tag1);
        mocker.replay();
        endpoint.setConnected(false);
        ((IDataTagChanger) controller).onRemoveDataTag(tag1, new ChangeReport()); //triggers reconnect
        assertEquals(Arrays.asList(CONNECTION_LOST, OK), f.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void errorOnInitializeShouldSendFail () throws ExecutionException, InterruptedException, TimeoutException {
        controller.setEndpoint(new ExceptionTestEndpoint());
        assertThrows(OPCCommunicationException.class, () -> controller.initialize("uri", sourceTags.values()));
        assertEquals(Collections.singletonList(CONNECTION_FAILED), f.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void errorOnInitializeAfterLostConnectionShouldSendBoth () throws ExecutionException, InterruptedException, TimeoutException {
        controller.setEndpoint(new ExceptionTestEndpoint());
        ((IDataTagChanger) controller).onRemoveDataTag(tag1, new ChangeReport()); //triggers reconnect
        assertEquals(Arrays.asList(CONNECTION_LOST, CONNECTION_FAILED), f.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }
}
