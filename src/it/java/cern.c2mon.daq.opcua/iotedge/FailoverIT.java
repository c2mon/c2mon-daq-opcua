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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.*;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FailoverIT {
    private static ConnectionResolver connectionResolver;
    private static Map.Entry<ConnectionResolver.OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> active;
    private static Map.Entry<ConnectionResolver.OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> fallback;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    final NonTransparentRedundancyTypeNode redundancyMock = mock(NonTransparentRedundancyTypeNode.class);

    @Autowired TestListeners.Pulse pulseListener;
    @Autowired Controller controller;
    @Autowired FailoverProxy failoverProxy;
    @Autowired FailoverTestEndpoint testEndpoint;

    @BeforeAll
    public static void setupContainers() {
        connectionResolver = new ConnectionResolver();
        active = connectionResolver.addImage();
        fallback = connectionResolver.addImage();
    }

    @AfterAll
    public static void tearDownContainers() {
        connectionResolver.close();
    }

    @BeforeEach
    public void setupEndpoint() {
        try {
            fallback.getValue().setConnectionCut(true);
        } catch (Exception ignored) {
            //toxiproxy will throw a Runtime error when attempting to cut a connection that's already cut
        }
        active.getValue().setConnectionCut(false);

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
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controller.stop();
    }

    @Test
    public void coldFailoverShouldReconnectClient() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        mockColdFailover();
        connect();
        cutConnection();

        final var reconnect = pulseListener.listen();
        fallback.getValue().setConnectionCut(false);
        Assertions.assertEquals(EndpointListener.EquipmentState.OK, reconnect.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        mockColdFailover();
        connect();
        cutConnection();

        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        fallback.getValue().setConnectionCut(false);
        Assertions.assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));

        //cleanUp
        active.getValue().setConnectionCut(false);
        fallback.getValue().setConnectionCut(true);
    }

    @Test
    public void restartServerWithColdFailoverShouldReconnectAndResubscribe() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        mockColdFailover();
        connect();

        //restart server
        final var connectionLost = pulseListener.listen();
        active.getKey().close();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        final var connectionRegained = pulseListener.getStateUpdate();
        active.getKey().restart();

        assertEquals(EndpointListener.EquipmentState.OK, connectionRegained.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }
    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        mockColdFailover();
        connect();
        cutConnection();

        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        active.getValue().setConnectionCut(false);
        Assertions.assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    private void connect() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        controller.connect(active.getKey().getUri());
        controller.subscribeTag(tag);
        pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
    }

    private void cutConnection() throws InterruptedException, ExecutionException, TimeoutException {
        final var disconnected = pulseListener.listen();
        active.getValue().setConnectionCut(true);
        disconnected.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
    }

    private void mockColdFailover() {
        expect(redundancyMock.getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.Cold))
                .anyTimes();
        expect(redundancyMock.getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{fallback.getKey().getUri()}))
                .anyTimes();
        EasyMock.replay(redundancyMock);
    }

}
