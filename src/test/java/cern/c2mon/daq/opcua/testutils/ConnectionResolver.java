package cern.c2mon.daq.opcua.testutils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("resolver")
public class ConnectionResolver {

    @AllArgsConstructor
    public enum Ports {
        PILOT(8890),
        SIMENGINE(4841),
        IOTEDGE(50000);
        int port;
    }

    ApplicationContext applicationContext;

    private final List<Map.Entry<OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy>> imgProxies = new ArrayList<>();
    private final Network network = Network.newNetwork();
    private final ToxiproxyContainer toxiProxyContainer;

    public ConnectionResolver(@Autowired ApplicationContext applicationContext, @Autowired OpcUaImage.Edge edge) {
        this.applicationContext = applicationContext;
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
        toxiProxyContainer.start();
        addImage(edge);
    }

    public Map.Entry<OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> getProxyAt(int position) {
        if (imgProxies.size() < position + 1) {
            return addImage(applicationContext.getBean(OpcUaImage.EdgePrototype.class));
        } else {
            return imgProxies.get(position);
        }
    }

    private Map.Entry<OpcUaImage.Edge, ToxiproxyContainer.ContainerProxy> addImage(OpcUaImage.Edge img) {
        final var e = Map.entry(img, img.startWithProxy(toxiProxyContainer, network));
        imgProxies.add(e);
        return e;
    }

    public void close() {
        imgProxies.forEach(e -> e.getKey().close());
        toxiProxyContainer.close();
    }

    public static class OpcUaImage {
        @Component(value = "edgeProto")
        @Scope(value = "prototype")
        public static class EdgePrototype extends Edge {}

        @Component(value = "edge")
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

        @Getter
        @Component(value = "venus")
        public static class BasicVenus extends OpcUaImage {
            private final String pilotUri;
            private final String simEngineUri;

            @Autowired
            public BasicVenus(@Value("${venus.environment:sim_BASIC.short.xml}") String environment) {
                image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                        .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                        .withEnv("SIMCONFIG", environment)
                        .withExposedPorts(Ports.PILOT.port, Ports.SIMENGINE.port);
                image.start();
                pilotUri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.PILOT.port);
                simEngineUri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.SIMENGINE.port);
                log.info("Venus servers ready. PILOT at: {}, SIMENGINE at {}. ", pilotUri, simEngineUri);
            }

            public Integer getMappedPort(int port) {
                return image.getMappedPort(port);
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