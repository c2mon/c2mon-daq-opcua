package it.simengine;

import it.ConnectionResolverBase;
import it.ServerStartupCheckerThread;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Runs before all tests across different classes
 */
@Slf4j
public class SimConnectionResolver extends ConnectionResolverBase {

    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        super.beforeAll(context, "simEngineDockerImage");
    }

    public void initialize() throws InterruptedException {
        image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forListeningPort())
                .withCommand(" --unsecuretransport --expose=4840-4940 --expose=8800-8900 --env 'SIMCONFIG=sim_BASIC.short.xml'")
                .withNetworkMode("host");
        image.start();

        log.info("Servers starting... ");
        ServerStartupCheckerThread pilotChecker = scheduleServerCheckAndExtractAddress(8890, "pilot");
        ServerStartupCheckerThread simEngineChecker = scheduleServerCheckAndExtractAddress(4841, "simEngine");

        pilotChecker.getThread().join(3000);
        simEngineChecker.getThread().join(3000);
        log.info("Servers ready");
    }

}
