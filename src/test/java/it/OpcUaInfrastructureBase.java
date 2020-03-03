package it;

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.connection.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.*;

@Slf4j
public abstract class OpcUaInfrastructureBase {

    protected static GenericContainer opcUa;
    protected static EquipmentAddress address;
    protected Endpoint endpoint;
    protected TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    protected MiloClientWrapper wrapper = new MiloClientWrapperImpl(address.getUriString(), SecurityPolicy.None);
    protected EventPublisher publisher = new EventPublisher();

    @BeforeEach
    public void setupEndpoint() {
        endpoint = new EndpointImpl(mapper, wrapper, publisher);
    }

    @BeforeAll
    public static void setupServer() throws URISyntaxException, InterruptedException {
        opcUa = new GenericContainer("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forListeningPort())
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        opcUa.start();
        address = new EquipmentAddress("opc.tcp://" + opcUa.getContainerInfo().getConfig().getHostName() + ":50000",
                "", "", "", 500, 500, true);

        log.info("Server starting... ");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.invokeAll(Collections.singletonList(new CheckServerState()), 3000, TimeUnit.MILLISECONDS);
    }

    @AfterAll
    public static void tearDownServer() {
        opcUa.stop();
        opcUa = null;
        address = null;
    }

    private static class CheckServerState implements Callable<Boolean> {
        @Override
        public Boolean call() {
            Endpoint endpoint = new EndpointImpl(
                    new TagSubscriptionMapperImpl(),
                    new MiloClientWrapperImpl(address.getUriString(), SecurityPolicy.None),
                    new EventPublisher());

            boolean serverRunning = false;
            while (!serverRunning) {
                try {
                    endpoint.initialize();
                    serverRunning = endpoint.isConnected();
                } catch (Exception e) {
//                    log.info("Server not yet ready ");
                }
            }
            try {
                endpoint.reset().get();
            } catch (InterruptedException|ExecutionException e) {
                log.error("Could not restart endpoint." , e);
            }
            log.info("Server ready");
            return serverRunning;
        }
    }

}
