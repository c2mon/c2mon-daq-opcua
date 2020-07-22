package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public abstract class EdgeTestBase {

    public static final int IOTEDGE  = 50000;
    private static final Network network = Network.newNetwork();
    private static final ToxiproxyContainer toxiProxyContainer;
    public static EdgeImage active;
    public static EdgeImage fallback;

    static {
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
    }

    @BeforeAll
    public static void setupContainers() {
        log.info("Starting EdgeTestBase containers");
        toxiProxyContainer.start();
        active = new EdgeImage(toxiProxyContainer, network);
        fallback = new EdgeImage(toxiProxyContainer, network);
        fallback.proxy.setConnectionCut(true);
    }

    @AfterAll
    public static void stopContainers() {
        log.info("Stopping EdgeTestBase containers");
        toxiProxyContainer.stop();
        active.image.stop();
        fallback.image.stop();

    }

    protected static IMessageSender.EquipmentState cutConnection(TestListeners.TestListener listener, EdgeImage img) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Cutting connection.");
        final CompletableFuture<IMessageSender.EquipmentState> connectionLost = listener.listen();
        img.proxy.setConnectionCut(true);
        return connectionLost.thenApply(c -> {
            log.info("Connection cut.");
            return c;
        }).get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    protected static IMessageSender.EquipmentState uncutConnection(TestListeners.TestListener listener, EdgeImage img) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Reestablishing connection.");
        final CompletableFuture<IMessageSender.EquipmentState> connectionRegained = listener.listen();
        img.proxy.setConnectionCut(false);
        return connectionRegained.thenApply(c -> {
            log.info("Connection reestablished.");
            return c;
        }).get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    public static class EdgeImage {

        public GenericContainer image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withExposedPorts(IOTEDGE);
        public ToxiproxyContainer.ContainerProxy proxy;

        @Getter
        public String uri;

        public EdgeImage(ToxiproxyContainer proxyContainer, Network network) {
            image.withNetwork(network).start();
            proxy = proxyContainer.getProxy(image, IOTEDGE);
            uri = "opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort();
            log.info("Edge server ready at {}. ", uri);
        }
    }
}