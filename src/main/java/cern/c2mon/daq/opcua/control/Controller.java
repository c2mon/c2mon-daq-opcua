package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;

/**
 * The {@link Controller} represents a one-to-many mapping to the {@link Endpoint}s and handles all actions directed at
 * them.
 */
public interface Controller {

    /**
     * Initialize supervision and connection monitoring to the active server.
     * @param endpoint           the currently connected {@link Endpoint}
     * @param redundantAddresses the addresses of the servers in the redundant server set not including the active
     *                           server URI
     * @throws OPCUAException if an error occurred when setting up connection monitoring
     */
    void initialize(Endpoint endpoint, String... redundantAddresses) throws OPCUAException;

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    void stop();

    /**
     * Subscribes the {@link NodeId}s in the {@link ItemDefinition}s to the corresponding {@link SubscriptionGroup} on
     * the OPC UA server or on all servers in a redundant server set.
     * @param groupsWithDefinitions a Map of {@link SubscriptionGroup}s and the {@link ItemDefinition}s to subscribe to
     *                              the groups.
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     */
    Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions);

    /**
     * Unsubscribe from the {@link NodeId} in the {@link ItemDefinition} on the OPC UA server or on all servers in a
     * redundant server set.
     * @param definition the {@link ItemDefinition} to unsubscribe from.
     * @return whether the removal from subscription was completed successfully at the currently active server. False if
     * the {@link ItemDefinition} was not subscribed in the first place.
     */
    boolean unsubscribe(ItemDefinition definition);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link SourceDataTagQualityCode} of the reading
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException;

    /**
     * Call the method node associated with the definition. It no method node is specified within the definition, it is
     * assumed that the primary node is the method node, and the first object node ID encountered during browse is used
     * as object node.
     * @param definition the definition containing the nodeId of Method which shall be called
     * @param arg        the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException;

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    boolean write(NodeId nodeId, Object value) throws OPCUAException;
}
