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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        try {
            fallback.getValue().setConnectionCut(true);
        } catch (Exception ignored) {
            //toxiproxy will throw a runtime error when attempting to cut a connection that's already cut
        }
        active.getValue().setConnectionCut(false);

        log.info("############ SET UP ############");
        pulseListener.setDebugEnabled(true);
        pulseListener.setSourceID(tag.getId());
        testEndpoint.setRedundancyMock(redundancyMock);
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(failoverProxy, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(failoverProxy, "endpoint", testEndpoint);

        mockColdFailover();
        connect();
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controller.stop();
    }

    @Test
    public void coldFailoverShouldReconnect() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldReconnectClient");
        cutConnection();
        Assertions.assertEquals(EndpointListener.EquipmentState.OK, uncutConnection(fallback.getValue()));
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldResumeSubscriptions");
        cutConnection();
        uncutConnection(fallback.getValue());
        resetListenerAndAssertTagUpdate();
    }

    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("regainActiveConnectionWithColdFailoverShouldResumeSubscriptions");
        cutConnection();
        uncutConnection(active.getValue());
        resetListenerAndAssertTagUpdate();
    }

    @Test
    public void restartServerWithColdFailoverShouldReconnectAndResubscribe() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("restartServerWithColdFailoverShouldReconnectAndResubscribe");
        //stop server
        final var connectionLost = pulseListener.listen();
        active.getKey().close();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

        //restart server
        final var connectionRegained = pulseListener.listen();
        active.getKey().restart();
        connectionRegained.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

        resetListenerAndAssertTagUpdate();
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

    private EndpointListener.EquipmentState uncutConnection(ToxiproxyContainer.ContainerProxy proxy) throws InterruptedException, ExecutionException, TimeoutException {
        final var connectionRegained = pulseListener.listen();
        proxy.setConnectionCut(false);
        return connectionRegained.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
    }

    private void resetListenerAndAssertTagUpdate() {
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }
}
