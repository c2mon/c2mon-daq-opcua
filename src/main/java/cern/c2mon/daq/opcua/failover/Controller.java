package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;

/**
 * Classes implementing this interface present a specific kind handler for redundancy modes with the responsibility of
 * monitoring the connection state to the active server and of handling failover when needed. Currently, only {@link
 * ColdFailoverDecorator} is supported of the OPC UA redundancy model. To add support for custom or vendor-specifc redundancy
 * models, a class implementing FailoverMode should be created and references in {@link
 * cern.c2mon.daq.opcua.AppConfigProperties}.
 */
public interface Controller {

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    void stop();

    void connect(String uri) throws OPCUAException;

    /**
     * Execute the required steps to monitor the connection to the active server and to failover on demand to a backup
     * server
     * @param redundantAddresses the addresses of the servers in the redundant server set not including the active
     *                           server URI
     * @throws OPCUAException if an error ocurred when setting up connection monitoring
     */
    void initializeMonitoring(String[] redundantAddresses) throws OPCUAException;

    /**
     * Fetch the currently active endpoint on which all actions are executed
     * @return the currently active endpoint of a redundant server set.
     */
    Endpoint currentEndpoint();

    Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions);

    boolean unsubscribe(ItemDefinition definition);
}
