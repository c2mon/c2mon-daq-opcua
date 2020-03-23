package it.iotedge;

import it.ServerStartupCheckerThread;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Runs before all tests across different classes
 */
@Slf4j
public class EdgeConnectionResolver implements BeforeAllCallback, ExtensionContext.Store.CloseableResource, ParameterResolver {

    private static GenericContainer image;

    private static boolean started = false;

    private static String address;

    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        if (!started) {
            started = true;
            initialize();

            // Registers a callback hook when the root test context is shut down
            getStore(context).put("edgeDockerImage", this);
        }
    }

    @Override
    public void close() {
        image.stop();
    }


    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return address;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == String.class) ;
    }

    public void initialize() throws InterruptedException {
        image =  new GenericContainer("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forListeningPort())
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        image.start();

        log.info("Server starting... ");
        address = "opc.tcp://" + image.getContainerInfo().getConfig().getHostName() + ":50000";
        log.info("Server ready");

        ServerStartupCheckerThread checkServerState = new ServerStartupCheckerThread(address);
        checkServerState.getThread().join(3000);
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    }

}
