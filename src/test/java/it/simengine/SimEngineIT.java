package it.simengine;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapper;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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

        pilotWrapper = new MiloClientWrapperImpl(serverAddresses.get("pilot"), SecurityPolicy.None);
        simEngineWrapper = new MiloClientWrapperImpl(serverAddresses.get("simEngine"), SecurityPolicy.None);
        pilot = new EndpointImpl(pilotWrapper, mapper, publisher);
        simEngine = new EndpointImpl(simEngineWrapper, mapper, publisher);
    }

    // TODO: must authenticate with certificate
    public void testConnection(Map<String, String> serverAddresses) {
        pilot.initialize(false);
        Assertions.assertDoesNotThrow(()-> pilot.isConnected());
    }


}
