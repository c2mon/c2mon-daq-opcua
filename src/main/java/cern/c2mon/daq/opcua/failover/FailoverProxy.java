package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;

import java.util.Collection;

public interface FailoverProxy {

    void initialize(String uri) throws OPCUAException;

    Endpoint getActiveEndpoint();

    Collection<Endpoint> getPassiveEndpoints();
}
