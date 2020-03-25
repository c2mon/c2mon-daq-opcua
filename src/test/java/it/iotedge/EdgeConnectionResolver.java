package it.iotedge;

import cern.c2mon.daq.opcua.downstream.NoSecurityCertifier;
import it.ConnectionResolverBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Runs before all tests across different classes
 */
@Slf4j
public class EdgeConnectionResolver extends ConnectionResolverBase {

    public static String ADDRESS_KEY = "edge";
    private static int PORT = 50000;

    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        this.certifier = new NoSecurityCertifier();
        super.beforeAll(context, "edgeDockerImage");
    }

    public void initialize() {
        image =  new GenericContainer("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        image.start();

        log.info("Servers starting... ");
        extractAddress(PORT, ADDRESS_KEY);
        log.info("Servers ready");
    }
}
