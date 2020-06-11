package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;

public interface FailoverMode {

    void initialize(Endpoint endpoint, String[] redundantAddresses);

    Endpoint currentEndpoint();
}
