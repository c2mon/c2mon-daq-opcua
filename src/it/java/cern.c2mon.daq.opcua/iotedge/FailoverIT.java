package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestFailoverProxy;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
public class FailoverIT {
    private static GenericContainer container;
    private static ToxiproxyContainer toxiProxyContainer;
    private static ToxiproxyContainer.ContainerProxy proxy;
    private static final int EDGE_PORT = 50000;
    private final ISourceDataTag tag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    private final ISourceDataTag alreadySubscribedTag = ServerTagFactory.DipData.createDataTag();
    private final TestListeners.Pulse pulseListener = new TestListeners.Pulse();

    @Autowired Controller controller;

    @Autowired TestFailoverProxy testFailoverProxy;

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
    public void setupEndpoint() {
        log.info("############ SET UP ############");
        pulseListener.setDebugEnabled(true);

        pulseListener.setSourceID(tag.getId());
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(controller, "failover", testFailoverProxy);
        ReflectionTestUtils.setField(testFailoverProxy, "endpointListener", pulseListener);
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
    public void mockColdFailover() throws OPCUAException {
        testFailoverProxy.setCurrentFailover(RedundancySupport.Cold);

        controller.connect("opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort());
        controller.subscribeTags(Collections.singletonList(alreadySubscribedTag));
        log.info("Client ready");
        log.info("############ TEST ############");
        pulseListener.reset();

    }
}
