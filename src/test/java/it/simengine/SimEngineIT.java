package it.simengine;

import cern.c2mon.daq.opcua.downstream.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

@ExtendWith(SimConnectionResolver.class)
public class SimEngineIT {

    private static TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    private static MiloClientWrapper pilotWrapper;
    private static MiloClientWrapper simEngineWrapper;
    private static EventPublisher publisher = new EventPublisher();
    private static Endpoint pilot;
    private static Endpoint simEngine;


     @BeforeAll
    public static void setup(Map<String, String> serverAddresses) {
        pilotWrapper = new MiloSelfSignedClientWrapperImpl(serverAddresses.get("pilot"), new NoSecurityCertifier());
        simEngineWrapper = new MiloSelfSignedClientWrapperImpl(serverAddresses.get("simEngine"), new NoSecurityCertifier());
        pilot = new EndpointImpl(pilotWrapper, mapper, publisher);
        simEngine = new EndpointImpl(simEngineWrapper, mapper, publisher);
    }

    //TODO: must authenticate with certificate
    @Test
    public void testPilotConnection(Map<String, String> serverAddresses) {
        pilot.initialize(false);
        Assertions.assertDoesNotThrow(()-> pilot.isConnected());
    }
}
