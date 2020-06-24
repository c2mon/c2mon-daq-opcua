package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;

import java.util.List;

public interface FailoverMode {

    void initialize(String uri, Endpoint endpoint, String[] redundantAddresses) throws OPCUAException;

    void disconnect() throws OPCUAException;

    Endpoint currentEndpoint();

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to disconnect.
     */
    List<Endpoint> passiveEndpoints();
}
