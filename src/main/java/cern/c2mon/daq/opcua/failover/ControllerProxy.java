package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;

/**
 * Classes implementing this interface present a specific kind handler for redundancy modes with the responsibility of
 * monitoring the connection state to the active server and of handling failover when needed. Currently, only {@link
 * ColdFailover} is supported of the OPC UA redundancy model. To add support for custom or vendor-specifc redundancy
 * models, a class implementing FailoverMode should be created and references in {@link
 * cern.c2mon.daq.opcua.AppConfigProperties}.
 */
public interface ControllerProxy {

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    void stop();

    void connect(String uri) throws OPCUAException;

    Controller getController();

}
