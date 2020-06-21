package cern.c2mon.daq.opcua.testutils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
public class ConnectionResolver {
    @AllArgsConstructor
    public enum Ports {
        PILOT(8890),
        SIMENGINE(4841),
        IOTEDGE(50000);
        int port;
    }

    private final List<OpcUaImage.Edge> images = new ArrayList<>();
    private final List<ToxiproxyContainer.ContainerProxy> proxies = new ArrayList<>();
    private final Network network = Network.newNetwork();
    private ToxiproxyContainer toxiProxyContainer;

    public ConnectionResolver() {
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
        toxiProxyContainer.start();
    }

    public Map.Entry<OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> addImage() {
        final OpcUaImage.Edge img = new OpcUaImage.Edge();
        final ToxiproxyContainer.ContainerProxy proxy = img.startWithProxy(toxiProxyContainer, network);
        proxies.add(proxy);
        images.add(img);
        return Map.entry(img, proxy);
    }

    public void close() {
        for (var i : images) {
            i.close();
        }
        toxiProxyContainer.close();
    }

    public static class OpcUaImage {

        public static class Venus extends OpcUaImage {
            @Getter
            public String pilotUri;

            @Getter
            public String simEngineUri;

            public Venus(String environment) {
                image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                        .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                        .withEnv("SIMCONFIG", environment)
                        .withExposedPorts(Ports.PILOT.port, Ports.SIMENGINE.port);
            }

            public void startStandAlone() {
                image.start();
                pilotUri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.PILOT.port);
                simEngineUri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.SIMENGINE.port);
                log.info("Venus servers ready. PILOT at: {}, SIMENGINE at {}. ", pilotUri, simEngineUri);
            }
            public Integer getMappedPort(int port) {
                return image.getMappedPort(port);
            }
        }

        public static class Edge extends OpcUaImage {
            @Getter
            public String uri;

            public Edge() {
                image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                        .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                        .withCommand("--unsecuretransport")
                        .withExposedPorts(Ports.IOTEDGE.port);
            }

            public ToxiproxyContainer.ContainerProxy startWithProxy(ToxiproxyContainer proxyContainer, Network network) {
                image.withNetwork(network).start();
                ToxiproxyContainer.ContainerProxy proxy = proxyContainer.getProxy(image, Ports.IOTEDGE.port);
                uri = "opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort();
                log.info("Edge server ready at {}. ", uri);
                return proxy;
            }

            public void startStandAlone() {
                image.start();
                uri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.IOTEDGE.port);
                log.info("Edge server ready at {}. ", uri);
            }
        }

        protected GenericContainer image;

        public void trustCertificates() throws IOException, InterruptedException {
            log.info("Trust certificate.");
            image.execInContainer("mkdir", "pki/trusted");
            image.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");
        }

        public void cleanUpCertificates() throws IOException, InterruptedException {
            log.info("Cleanup.");
            image.execInContainer("rm", "-r", "pki/trusted");
        }

        public void restart() {
            image.start();
        }

        public void close() {
            image.stop();
            image.close();
        }
    }
}