package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;

public interface FailoverProxy {

    void initialize(String uri) throws OPCUAException;

    void disconnect() throws OPCUAException;

    Endpoint getEndpoint();
}
