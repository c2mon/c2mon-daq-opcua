package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;

import java.util.Collection;

public interface FailoverProxy {


    /**
     * Delegates failover handling to an appropriate {@link FailoverMode} according to the application configuration or,
     * secondarily, according to server capabilities.
     * @param uri the URI of the active server
     * @throws OPCUAException if an exception occurrs when initially connecting to the active server.
     */
    void initialize(String uri) throws OPCUAException;

    /**
     * Fetch the currently active endpoint on which all actions are executed
     * @return the currently active endpoint of a redundant server set.
     */
    Endpoint getActiveEndpoint();

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to
     * disconnect. If the connected server is not within a redundant server set, an empty list is returned.
     */
    Collection<Endpoint> getPassiveEndpoints();
}
