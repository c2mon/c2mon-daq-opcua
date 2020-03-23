package it.iotedge;

import cern.c2mon.daq.opcua.downstream.NoSecurityCertifier;
import it.ConnectionResolverBase;
import it.ServerStartupCheckerThread;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Runs before all tests across different classes
 */
@Slf4j
public class EdgeConnectionResolver extends ConnectionResolverBase {

    public static String ADDRESS_KEY = "edge";

    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        this.certifier = new NoSecurityCertifier();
        super.beforeAll(context, "edgeDockerImage");
    }

    public void initialize() throws InterruptedException {
        image =  new GenericContainer("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forListeningPort())
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        image.start();


        log.info("Servers starting... ");
        ServerStartupCheckerThread checkServerState = scheduleServerCheckAndExtractAddress(50000, ADDRESS_KEY);
        checkServerState.getThread().join(3000);
        log.info("Servers ready");
    }
}
