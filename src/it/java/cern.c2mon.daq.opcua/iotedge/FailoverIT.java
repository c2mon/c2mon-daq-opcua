package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.testutils.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.*;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
public class FailoverIT {
    private static ConnectionResolver.Edge active;
    private static ConnectionResolver.Edge fallback;

    private final ISourceDataTag tag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    private final TestListeners.Pulse pulseListener = new TestListeners.Pulse();
    final NonTransparentRedundancyTypeNode redundancyMock = mock(NonTransparentRedundancyTypeNode.class);

    @Autowired Controller controller;
    @Autowired FailoverProxy failoverProxy;
    @Autowired FailoverTestEndpoint testEndpoint;

    @BeforeAll
    public static void setupContainers() {
        active = new ConnectionResolver.Edge();
        fallback = new ConnectionResolver.Edge();

    }

    @AfterAll
    public static void tearDownContainers() {
        active.close();
        fallback.close();
    }

    @BeforeEach
    public void setupEndpoint() {
        active.getProxy().setConnectionCut(false);
        fallback.getProxy().setConnectionCut(true);

        log.info("############ SET UP ############");
        pulseListener.setDebugEnabled(true);
        pulseListener.setSourceID(tag.getId());

        testEndpoint.setRedundancyMock(redundancyMock);
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(failoverProxy, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(failoverProxy, "endpoint", testEndpoint);
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        log.info("############ CLEAN UP ############");
        final var f = pulseListener.listen();
        controller.stop();
        try {
            f.get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | CompletionException | ExecutionException e) {
            // assure that server has time to disconnect, fails for test on failed connections
        }
        controller = null;
    }

    @Test
    public void coldFailoverShouldReconnectClient() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        mockColdFailover();
        connectAndDisconnect();

        final var reconnect = pulseListener.listen();
        fallback.getProxy().setConnectionCut(false);
        Assertions.assertEquals(EndpointListener.EquipmentState.OK, reconnect.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        mockColdFailover();
        connectAndDisconnect();

        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        fallback.getProxy().setConnectionCut(false);
        Assertions.assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    private void connectAndDisconnect() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        controller.connect(active.getUri());
        controller.subscribeTag(tag);
        pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

        final var disconnected = pulseListener.listen();
        active.getProxy().setConnectionCut(true);
        disconnected.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
    }

    private void mockColdFailover() {
        expect(redundancyMock.getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.Cold))
                .anyTimes();
        expect(redundancyMock.getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{fallback.getUri()}))
                .anyTimes();
        EasyMock.replay(redundancyMock);
    }
}
