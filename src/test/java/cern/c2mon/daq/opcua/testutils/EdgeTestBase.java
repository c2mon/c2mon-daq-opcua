package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.EndpointListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public abstract class EdgeTestBase {

    private static final int IOTEDGE  = 50000;
    static final Network network = Network.newNetwork();
    static final ToxiproxyContainer toxiProxyContainer;
    public static final EdgeImage active;
    public static final EdgeImage fallback;

    static {
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
        toxiProxyContainer.start();
        active = new EdgeImage(toxiProxyContainer, network);
        fallback = new EdgeImage(toxiProxyContainer, network);
    }

    protected static EndpointListener.EquipmentState cutConnection(TestListeners.TestListener listener, EdgeImage img) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("cutting connection");
        final var connectionLost = listener.listen();
        img.proxy.setConnectionCut(true);
        return connectionLost.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    protected static EndpointListener.EquipmentState uncutConnection(TestListeners.TestListener listener, EdgeImage img) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("uncutting connection");
        final var connectionRegained = listener.listen();
        img.proxy.setConnectionCut(false);
        return connectionRegained.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
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

        public void trustCertificates() throws IOException, InterruptedException {
            log.info("Trust certificate.");
            image.execInContainer("mkdir", "pki/trusted");
            image.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");
        }

        public void cleanUpCertificates() throws IOException, InterruptedException {
            log.info("Cleanup.");
            image.execInContainer("rm", "-r", "pki/trusted");
        }
    }
}