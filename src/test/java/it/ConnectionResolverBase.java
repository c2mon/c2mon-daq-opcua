package it;

import cern.c2mon.daq.opcua.downstream.Certifier;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.Map;

public abstract class ConnectionResolverBase implements BeforeAllCallback, ExtensionContext.Store.CloseableResource, ParameterResolver {

    protected static GenericContainer image;

    private static boolean started = false;

    protected static Map<String, String> serverAddresses = new HashMap<>();

    protected Certifier certifier;

    public void beforeAll(ExtensionContext context, String callbackHook) throws InterruptedException {
        if (!started) {
            started = true;
            initialize();

            // Registers a callback hook when the root test context is shut down
            getStore(context).put(callbackHook, this);
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

    public abstract void initialize() throws InterruptedException;

    protected ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    }

    protected ServerStartupCheckerThread scheduleServerCheckAndExtractAddress(int port, String key) {
        String hostName = image.getContainerInfo().getConfig().getHostName().toLowerCase();
        final String address = "opc.tcp://" + hostName + ":" + port;
        serverAddresses.put(key, address);
        return new ServerStartupCheckerThread(address, certifier);
    }

}
