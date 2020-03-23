package it.simengine;

import it.ServerStartupCheckerThread;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs before all tests across different classes
 */
@Slf4j
public class SimConnectionResolver implements BeforeAllCallback, ExtensionContext.Store.CloseableResource, ParameterResolver {

    private static GenericContainer image;

    private static boolean started = false;

    private static Map<String, String> serverAddresses = new HashMap<>();

    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        if (!started) {
            started = true;
            initialize();

            // Registers a callback hook when the root test context is shut down
            getStore(context).put("simEngineDockerImage", this);
        }
    }

    @Override
    public void close() {
        image.stop();
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return serverAddresses;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == Map.class) ;
    }

    public void initialize() throws InterruptedException {
        image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forListeningPort())
                .withCommand(" --unsecuretransport --expose=4840-4940 --expose=8800-8900 --env 'SIMCONFIG=sim_BASIC.short.xml'")
                .withNetworkMode("host");
        image.start();

        log.info("Servera starting... ");
        ServerStartupCheckerThread pilotChecker = scheduleServerCheckAndExtractAddress(8890, "pilot");
        ServerStartupCheckerThread simEngineChecker = scheduleServerCheckAndExtractAddress(4841, "simEngine");

        pilotChecker.getThread().join(3000);
        simEngineChecker.getThread().join(3000);
        log.info("Servers ready");
    }

    private ServerStartupCheckerThread scheduleServerCheckAndExtractAddress(int port, String key) {
        final String address = "opc.tcp://" + image.getContainerInfo().getConfig().getHostName() + ":" + port;
        serverAddresses.put(key, address);
        return new ServerStartupCheckerThread(address);
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    }

}
