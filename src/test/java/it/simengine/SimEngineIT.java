package it.simengine;

import cern.c2mon.daq.opcua.downstream.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import it.ConnectionResolver;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class SimEngineIT {

    private static int PILOT_PORT = 8890;
    private static int SIMENGINE_PORT = 4841;

    private MiloClientWrapper pilotWrapper;
    private MiloClientWrapper simEngineWrapper;
    private static Endpoint pilot;
    private static Endpoint simEngine;
    private static ConnectionResolver resolver;


    @BeforeAll
    public static void startServers() {
        GenericContainer image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                .withEnv("SIMCONFIG", "sim_BASIC.short.xml")
                .withNetworkMode("host");

        resolver = new ConnectionResolver(image);
        resolver.initialize();
    }

    @AfterAll
    public static void stopServers() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setupTestcontainers() {
        pilotWrapper = new MiloClientWrapperImpl(resolver.getURI(PILOT_PORT), new NoSecurityCertifier());
        simEngineWrapper = new MiloClientWrapperImpl(resolver.getURI(SIMENGINE_PORT), new NoSecurityCertifier());
        pilot = new EndpointImpl(pilotWrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
        simEngine = new EndpointImpl(simEngineWrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
    }

    @AfterEach
    public void teardown() {
        pilot.reset();
        simEngine.reset();
    }

    @Test
    public void testPilotConnection() {
        pilot.initialize(false);
        Assertions.assertDoesNotThrow(()-> pilot.isConnected());
    }

    @Test
    public void testSimEngineConnection() {
        simEngine.initialize(false);
        Assertions.assertDoesNotThrow(()-> simEngine.isConnected());
    }
}
