package it;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapper;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
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
    protected static String address;
    protected Endpoint endpoint;
    protected TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    protected MiloClientWrapper wrapper;
    protected EventPublisher publisher = new EventPublisher();

    @BeforeEach
    public void setupEndpoint() throws ExecutionException, InterruptedException {
        wrapper = new MiloClientWrapperImpl(address, SecurityPolicy.None);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
    }

    @BeforeAll
    public static void setupServer() throws URISyntaxException, InterruptedException {
        opcUa = new GenericContainer("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forListeningPort())
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        opcUa.start();

        address = "opc.tcp://" + opcUa.getContainerInfo().getConfig().getHostName() + ":50000";

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
        public Boolean call() throws ExecutionException, InterruptedException {
            Endpoint endpoint = new EndpointImpl(
                    new MiloClientWrapperImpl(address, SecurityPolicy.None),
                    new TagSubscriptionMapperImpl(),
                    new EventPublisher());

            boolean serverRunning = false;
            while (!serverRunning) {
                try {
                    endpoint.initialize(false);
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
