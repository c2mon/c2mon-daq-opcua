package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverMode;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.failover.NoFailover;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;

@RequiredArgsConstructor
@Component(value = "testFailoverProxy")
public class TestFailoverProxy implements FailoverProxy, SessionActivityListener {

    @Lazy private final NoFailover noFailover;
    @Lazy private final FailoverMode coldFailover;
    private final Endpoint endpoint;

    private FailoverMode currentFailover;

    @Setter
    private String[] redundantUris = new String[0];

    public void setCurrentFailover(RedundancySupport mode) {
        switch (mode) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                currentFailover = coldFailover;
                break;
            default:
                currentFailover = noFailover;
        }
    }

    public void initialize(String uri) throws OPCUAException {
        if (currentFailover == null) {
            currentFailover = noFailover;
        }
        noFailover.initializeEndpoint(uri);
        currentFailover.initialize(uri, endpoint, redundantUris);
    }

    public Endpoint getActiveEndpoint() {
        return currentFailover.currentEndpoint();
    }

    @Override
    public Collection<Endpoint> getPassiveEndpoints() {
        return currentFailover.passiveEndpoints();
    }
}
