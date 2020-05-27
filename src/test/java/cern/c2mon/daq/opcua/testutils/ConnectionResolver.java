package cern.c2mon.daq.opcua.testutils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;

@Slf4j
public class ConnectionResolver {

    public static class Venus extends ConnectionResolver {
        @Getter
        public String pilotUri;

        @Getter
        public String simEngineUri;

        protected Venus(GenericContainer image) {
            super(image);
            pilotUri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.PILOT.port);
            simEngineUri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.SIMENGINE.port);
            log.info("Venus servers ready. PILOT at: {}, SIMENGINE at {}. ", pilotUri, simEngineUri);
        }
    }

    public static class Edge extends ConnectionResolver {
        @Getter
        public String uri;

        protected Edge(GenericContainer image) {
            super(image);
            uri = "opc.tcp://" + image.getContainerIpAddress() + ":" + image.getMappedPort(Ports.IOTEDGE.port);
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

    public static ConnectionResolver.Venus resolveVenusServers(String environment) {
        GenericContainer image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                .withEnv("SIMCONFIG", environment)
                .withExposedPorts(Ports.PILOT.port, Ports.SIMENGINE.port);
        return new ConnectionResolver.Venus(image);
    }

    public static ConnectionResolver.Edge resolveIoTEdgeServer() {
        GenericContainer image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withExposedPorts(Ports.IOTEDGE.port);
        return new ConnectionResolver.Edge(image);
    }

    private final GenericContainer image;


    protected ConnectionResolver(GenericContainer image) {
        this.image = image;
        image.start();
    }

    public void trustCertificates () throws IOException, InterruptedException {
        log.info("Trust certificate.");
        image.execInContainer("mkdir", "pki/trusted");
        image.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");
    }

    public void cleanUpCertificates() throws IOException, InterruptedException {
        log.info("Cleanup.");
        image.execInContainer("rm", "-r", "pki/trusted");
    }

    public void initialize() {
        log.info("Server starting... ");
        image.start();
        log.info("Server ready");
    }

    public Integer getMappedPort(int port) {
        return image.getMappedPort(port);
    }

    public String getURI(int port) {
        String hostName = image.getContainerIpAddress();
        return "opc.tcp://" + hostName + ":" + port;
    }

    public void close() {
        image.stop();
        image.close();
    }
}
