package it;

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class OpcUaInfrastructureBase {

    protected static GenericContainer opcUa;
    protected static EquipmentAddress address;
    protected EndpointImpl endpoint;


    @BeforeEach
    public void setupEndpoint() {
        endpoint = new EndpointImpl(new TagSubscriptionMapperImpl());
    }

    @AfterEach
    public void cleanUp() {
        endpoint.reset();
        endpoint = null;
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
            EndpointImpl endpoint = new EndpointImpl(new TagSubscriptionMapperImpl());

            boolean serverRunning = false;
            while (!serverRunning) {
                try {
                    endpoint.initialize(address);
                    serverRunning = endpoint.isConnected();
                } catch (Exception e) {
                    // try again, the server needs a second to start
                }
            }
            endpoint.reset();
            return serverRunning;
        }
    }

}
