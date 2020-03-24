package it.iotedge;

import cern.c2mon.daq.opcua.downstream.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

@Slf4j
public abstract class EdgeITBase {

    protected Endpoint endpoint;
    protected TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    protected MiloClientWrapper wrapper;
    protected EventPublisher publisher = new EventPublisher();

    @BeforeEach
    public void setupEndpoint(Map<String, String> addresses) {
        wrapper = new MiloClientWrapperImpl(addresses.get(EdgeConnectionResolver.ADDRESS_KEY), new NoSecurityCertifier());
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
    }
}
