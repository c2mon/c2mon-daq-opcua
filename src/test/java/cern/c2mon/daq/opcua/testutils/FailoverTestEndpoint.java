package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.springframework.stereotype.Component;

@Component("failoverEndpoint")
public class FailoverTestEndpoint extends MiloEndpoint {

    @Setter
    ServerRedundancyTypeNode redundancyMock;

    public FailoverTestEndpoint(SecurityModule securityModule, EndpointSubscriptionListener subscriptionListener, RetryDelegate retryDelegate) {
        super(securityModule, subscriptionListener, retryDelegate);
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() {
        return redundancyMock;
    }
}
