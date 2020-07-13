package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;

public interface ControllerProxy {


    /**
     * Delegates failover handling to an appropriate {@link Controller} according to the application configuration or,
     * secondarily, according to server capabilities.
     * @param uri the URI of the active server
     * @throws OPCUAException if an exception occurrs when initially connecting to the active server.
     */
    void connect(String uri) throws OPCUAException;

    void stop();

    /**
     * Fetch the currently active endpoint on which all actions are executed
     * @return the currently active endpoint of a redundant server set.
     */
    Endpoint getActiveEndpoint();

    /**
     * Remove an ItemDefinition from all active and passive endpoints.
     * @return whether the ItemDefinition could be removed successfully from the active endpoint.
     */
    boolean unsubscribeDefinition(ItemDefinition definition);

    Map<UInteger, SourceDataTagQuality> subscribeToGroups(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions);
}
