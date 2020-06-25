package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ColdFailover;
import cern.c2mon.daq.opcua.failover.FailoverMode;
import cern.c2mon.daq.opcua.failover.NoFailover;
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
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
public class FailoverIT {
    @Autowired ConnectionResolver connectionResolver;
    @Autowired TestListeners.Pulse pulseListener;
    @Autowired Controller controller;
    @Autowired NoFailover noFailover;
    @Autowired FailoverMode coldFailover;
    @Autowired FailoverTestEndpoint testEndpoint;
    @Autowired AppConfig config;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final NonTransparentRedundancyTypeNode redundancyMock = niceMock(NonTransparentRedundancyTypeNode.class);

    private Map.Entry<ConnectionResolver.OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> active;
    private Map.Entry<ConnectionResolver.OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> fallback;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        active = connectionResolver.getProxyAt(0);
        fallback = connectionResolver.getProxyAt(1);

        try {
            fallback.getValue().setConnectionCut(true);
        } catch (Exception ignored) {
            //toxiproxy will throw a runtime error when attempting to cut a connection that's already cut
        }
        active.getValue().setConnectionCut(false);

        log.info("############ SET UP ############");
        pulseListener.setSourceID(tag.getId());
        testEndpoint.setRedundancyMock(redundancyMock);
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(coldFailover, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(noFailover, "endpoint", testEndpoint);
        ReflectionTestUtils.setField(testEndpoint, "endpointListener", pulseListener);

        mockColdFailover();

        controller.connect(active.getKey().getUri());
        controller.subscribeTag(tag);
        pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controller.stop();
    }

    @Test
    public void coldFailoverShouldReconnect() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldReconnectClient");
        cutConnection(active.getValue());
        triggerServerSwitch();
        Assertions.assertEquals(EndpointListener.EquipmentState.OK, uncutConnection(fallback.getValue()));
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldResumeSubscriptions");
        cutConnection(active.getValue());
        triggerServerSwitch();
        uncutConnection(fallback.getValue());
        resetListenerAndAssertTagUpdate();
    }

    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("regainActiveConnectionWithColdFailoverShouldResumeSubscriptions");
        cutConnection(active.getValue());
        triggerServerSwitch();
        TimeUnit.MILLISECONDS.sleep(TestUtils.TIMEOUT);
        uncutConnection(active.getValue());
        resetListenerAndAssertTagUpdate();
    }

    @Test
    public void longDisconnectShouldTriggerReconnectToAnyAvailableServer() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("longDisconnectShouldTriggerReconnectToAnyAvailableServer");
        cutConnection(active.getValue());
        TimeUnit.MILLISECONDS.sleep(config.getTimeout() + 1000);
        uncutConnection(fallback.getValue());
        resetListenerAndAssertTagUpdate();
    }

    @Test
    public void reconnectAfterLongDisconnectShouldCancelReconnection() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("restartServerWithColdFailoverShouldReconnectAndResubscribe");
        cutConnection(active.getValue());
        TimeUnit.MILLISECONDS.sleep(config.getTimeout() + 1000);
        uncutConnection(active.getValue());
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

    private void cutConnection(ToxiproxyContainer.ContainerProxy proxy) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("cutting connection");
        final var disconnected = pulseListener.listen();
        proxy.setConnectionCut(true);
        disconnected.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    private EndpointListener.EquipmentState uncutConnection(ToxiproxyContainer.ContainerProxy proxy) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("uncutting connection");
        final var connectionRegained = pulseListener.listen();
        proxy.setConnectionCut(false);
        return connectionRegained.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    private void resetListenerAndAssertTagUpdate() {
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));
    }

    private void triggerServerSwitch() {
        CompletableFuture.runAsync(() -> ((ColdFailover) coldFailover).triggerServerSwitch());
    }
}