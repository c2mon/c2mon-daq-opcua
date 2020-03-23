package it.iotedge;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapper;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.BeforeEach;

@Slf4j
public abstract class EdgeITBase {

    protected Endpoint endpoint;
    protected TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    protected MiloClientWrapper wrapper;
    protected EventPublisher publisher = new EventPublisher();

    @BeforeEach
    public void setupEndpoint(String address) {
        wrapper = new MiloClientWrapperImpl(address, SecurityPolicy.None);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
    }
}
