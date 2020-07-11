package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;

import java.util.List;

/**
 * Classes implementing this interface present a specific kind handler for redundancy modes with the responsibility of
 * monitoring the connection state to the active server and of handling failover when needed. Currently, only {@link
 * NoFailover} and {@link ColdFailover} are supported of the OPC UA redundancy model. To add support for custom or
 * vendor-specifc redundancy models, a class implementing FailoverMode should be created and references in {@link
 * cern.c2mon.daq.opcua.AppConfigProperties}.
 */
public interface FailoverMode {

    /**
     * Execute the required steps to monitor the connection to the active server and to failover on demand to a backup
     * server
     * @param uri                the URI of the active server
     * @param endpoint           an endpoint already connected to the currently active server
     * @param redundantAddresses the addresses of the servers in the redundant server set not including the active
     *                           server URI
     * @throws OPCUAException if an error ocurred when setting up connection monitoring
     */
    void initializeMonitoring(String uri, Endpoint endpoint, String[] redundantAddresses) throws OPCUAException;

    /**
     * Disconnect from all servers in the redundant server set
     * @throws OPCUAException if an error occurred during disconnection
     */
    void disconnect() throws OPCUAException;

    /**
     * Fetch the currently active endpoint on which all actions are executed
     * @return the currently active endpoint of a redundant server set.
     */
    Endpoint currentEndpoint();

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to
     * disconnect. If the connected server is not within a redundant server set, an empty list is returned.
     */
    List<Endpoint> passiveEndpoints();
}
