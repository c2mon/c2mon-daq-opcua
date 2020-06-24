package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.springframework.stereotype.Component;

@Component("failoverEndpoint")
public class FailoverTestEndpoint extends MiloEndpoint {

    @Setter
    ServerRedundancyTypeNode redundancyMock;

    public FailoverTestEndpoint(SecurityModule securityModule, RetryDelegate retryDelegate, TagSubscriptionMapper mapper, EndpointListener endpointListener) {
        super(securityModule, retryDelegate, mapper, endpointListener);
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() {
        return redundancyMock;
    }
}
