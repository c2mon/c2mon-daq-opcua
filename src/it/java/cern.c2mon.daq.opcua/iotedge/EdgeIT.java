package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.connection.EndpointListener.EquipmentState.CONNECTION_LOST;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
public class EdgeIT {
    private static GenericContainer container;
    private static ToxiproxyContainer toxiProxyContainer;
    private static ToxiproxyContainer.ContainerProxy proxy;
    private static final int EDGE_PORT = 50000;
    private final ISourceDataTag tag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    private final ISourceDataTag alreadySubscribedTag = ServerTagFactory.DipData.createDataTag();
    private final TestListeners.Pulse pulseListener = new TestListeners.Pulse();

    @Autowired
    Controller controller;

    @Autowired
    FailoverProxy failoverProxy;

    Endpoint endpoint;

    @BeforeAll
    public static void setupContainers() {
        Network network = Network.newNetwork();
        // manage lifecycles manually since we're shutting the containers down during testing
        container = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withNetwork(network)
                .withExposedPorts(EDGE_PORT);
        container.start();
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
        toxiProxyContainer.start();
        proxy = toxiProxyContainer.getProxy(container, EDGE_PORT);
    }

    @AfterAll
    public static void tearDownContainers() {
        container.stop();
        toxiProxyContainer.stop();
    }

    @BeforeEach
    public void setupEndpoint() throws OPCUAException, InterruptedException {
        log.info("############ SET UP ############");
        pulseListener.setDebugEnabled(true);

        pulseListener.setSourceID(tag.getId());
        endpoint = (Endpoint) ReflectionTestUtils.getField(failoverProxy, "endpoint");
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(endpoint, "endpointListener", pulseListener);

        controller.connect("opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort());
        controller.subscribeTags(Collections.singletonList(alreadySubscribedTag));
        log.info("Client ready");
        log.info("############ TEST ############");
        pulseListener.reset();
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
        proxy.setConnectionCut(false);
    }


    @Test
    public void connectToRunningServerShouldSendOK() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        final var stateUpdate = pulseListener.getStateUpdate();
        controller.connect("opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort());
        assertEquals(EndpointListener.EquipmentState.OK, stateUpdate.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void connectToBadServerShouldThrowErrorAndSendFAIL() throws InterruptedException, ExecutionException, TimeoutException {
        final var stateUpdate = pulseListener.getStateUpdate();
        assertThrows(OPCUAException.class, () -> controller.connect("opc.tcp://somehost/somepath"));
        assertEquals(EndpointListener.EquipmentState.CONNECTION_FAILED, stateUpdate.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Ignore
    public void stopServerForVeryLongShouldRestartEndpoint() throws InterruptedException, ExecutionException, TimeoutException {
        final var connectionLost = pulseListener.getStateUpdate();
        container.stop();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        CompletableFuture.runAsync(() -> {
            container.start();
            final var connectionRegained = pulseListener.getStateUpdate();
            pulseListener.setSourceID(alreadySubscribedTag.getId());

            final EndpointListener.EquipmentState reconnectState = assertDoesNotThrow(() -> connectionRegained.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
            assertEquals(EndpointListener.EquipmentState.OK, reconnectState);
        }, CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES)).join();
    }

    @Test
    public void restartServerShouldReconnectAndResubscribe() throws InterruptedException, ExecutionException, TimeoutException {
        final var connectionLost = pulseListener.getStateUpdate();
        container.stop();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        final var connectionRegained = pulseListener.getStateUpdate();
        pulseListener.setSourceID(alreadySubscribedTag.getId());
        container.start();

        assertEquals(EndpointListener.EquipmentState.OK, connectionRegained.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void regainedConnectionShouldContinueDeliveringSubscriptionValues() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        controller.subscribeTags(Collections.singletonList(tag));

        // Disconnect
        proxy.setConnectionCut(true);
        final var connectionLost = pulseListener.getStateUpdate();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

        // Setup listener on subscription
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        proxy.setConnectionCut(false);

        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void interruptedServerShouldSendLOST() {
        proxy.setConnectionCut(true);
        final EndpointListener.EquipmentState reconnectState = assertDoesNotThrow(() -> pulseListener.getStateUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
        assertEquals(CONNECTION_LOST, reconnectState);
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() throws ConfigurationException, CommunicationException {
        controller.subscribeTags(Collections.singletonList(tag));
        pulseListener.setSourceID(tag.getId());
        Object o = assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
        assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () throws ConfigurationException, CommunicationException {
        final ISourceDataTag tag = ServerTagFactory.Invalid.createDataTag();
        pulseListener.setSourceID(tag.getId());
        pulseListener.setThreshold(0);
        controller.subscribeTags(Collections.singletonList(tag));
        assertDoesNotThrow(() -> pulseListener.getTagInvalid().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithDeadband() throws ConfigurationException, CommunicationException {
        var tagWithDeadband = ServerTagFactory.RandomUnsignedInt32.createDataTag(10, (short) DeadbandType.Absolute.getValue(), 0);
        pulseListener.setSourceID(tagWithDeadband.getId());
        controller.subscribeTags(Collections.singletonList(tagWithDeadband));
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshProperTag () throws InterruptedException {
        pulseListener.setSourceID(tag.getId());
        controller.refreshDataTag(tag);
        Thread.sleep(2000);
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

}
