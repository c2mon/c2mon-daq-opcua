package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverMode;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
public class TestFailoverProxy implements FailoverProxy, SessionActivityListener {
    private final EndpointListener endpointListener;
    @Lazy private final FailoverMode noFailover;
    @Lazy private final FailoverMode coldFailover;

    private final Endpoint endpoint;
    private FailoverMode currentFailover;

    @Setter
    private String[] redundantUris;

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
        currentFailover.initialize(Map.entry(uri, endpoint), redundantUris, Collections.emptyList());
        endpoint.initialize(uri, Arrays.asList((SessionActivityListener) endpointListener, this));
    }

    @Override
    public void disconnect() throws OPCUAException {
        currentFailover.disconnect();
    }

    @Override
    public void onSessionInactive(UaSession session) {
        currentFailover.switchServers();
    }

    public Endpoint getEndpoint() {
        return currentFailover.currentEndpoint();
    }
}
