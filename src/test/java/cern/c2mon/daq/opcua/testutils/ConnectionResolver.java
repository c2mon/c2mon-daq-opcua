package cern.c2mon.daq.opcua.testutils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;

@Slf4j
@Getter
public class ConnectionResolver {

    public static class Venus extends ConnectionResolver {
        @Getter
        public String pilotUri;

        @Getter
        public String simEngineUri;

        public Venus(String environment) {
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

    public static class Edge extends ConnectionResolver {
        @Getter
        public String uri;

        private final ToxiproxyContainer toxiProxyContainer;

        @Getter
        private final ToxiproxyContainer.ContainerProxy proxy;


        public Edge() {
            Network network = Network.newNetwork();
            image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                    .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                    .withCommand("--unsecuretransport")
                    .withExposedPorts(Ports.IOTEDGE.port)
                    .withNetwork(network);
            image.start();
            toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
            toxiProxyContainer.start();
            proxy = toxiProxyContainer.getProxy(image, Ports.IOTEDGE.port);
            uri = "opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort();
            log.info("Edge server ready at {}. ", uri);


        }
    }

    @AllArgsConstructor
    public enum Ports {
        PILOT(8890),
        SIMENGINE(4841),
        IOTEDGE(50000);
        int port;
    }

    protected GenericContainer image;

    public void trustCertificates () throws IOException, InterruptedException {
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
