package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverMode;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestFailoverProxy implements FailoverProxy {
    private final FailoverMode noFailover;
    private final Endpoint endpoint;

    public void initialize(String uri) throws OPCUAException, InterruptedException {
        noFailover.initialize(endpoint, new String[0]);
        endpoint.initialize(uri);
    }

    public Endpoint getEndpoint() {
        return noFailover.currentEndpoint();
    }
}
