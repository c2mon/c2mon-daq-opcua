package cern.c2mon.daq.opcua.testutils;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;

@Slf4j
@AllArgsConstructor
public class ConnectionResolver {

    @AllArgsConstructor
    public enum Ports {
        PILOT(8890),
        SIMENGINE(4841),
        IOTEDGE(50000);
        int port;
    }

    public static ConnectionResolver resolveVenusServers(String environment) {
        GenericContainer image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                .withEnv("SIMCONFIG", environment)
                .withNetworkMode("host");
        return new ConnectionResolver(image);
    }

    public static ConnectionResolver resolveIoTEdgeServer() {
        GenericContainer image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        return new ConnectionResolver(image);
    }

    private final GenericContainer image;

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

    public String getURI(Ports port) {
        return getURI(port.port);
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
