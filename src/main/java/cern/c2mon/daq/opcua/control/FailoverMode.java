package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;

/**
 * Implementing classes are {@link ConcreteController}s that are connected to one or more servers in a non-transparent redundant
 * server set. They support the failover between servers in case of error.
 */
public interface FailoverMode {
    /**
     * Initiates a server failover. This method is periodically  when the client loses connectivity with the active
     * Server until connection can be reestablished.
     * @throws OPCUAException if connecting to a server of the redundant server set failed.
     */
    void switchServers() throws OPCUAException;
}
