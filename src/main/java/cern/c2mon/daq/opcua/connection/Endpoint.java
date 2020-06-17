package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This class presents an interface in between an C2MON OPC UA DAQ and the OPC UA client stack. Details on the thrown
 * {@link OPCUAException}s are given in the JavaDoc of the concrete implementation classes.
 */
public interface Endpoint {

    /**
     * Connects to a server through OPC UA.
     * @param uri the server address to connect to.
     * @throws OPCUAException       of type {@link CommunicationException} or {@link ConfigurationException}.
     * @throws InterruptedException if the method was interrupted during execution.
     */
    void initialize(String uri) throws OPCUAException, InterruptedException;

    /**
     * Disconnect from the server.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    void disconnect() throws OPCUAException;

    /**
     * Create and return a new subscription.
     * @param timeDeadband The subscription's publishing interval in milliseconds. If 0, the Server will use the fastest
     *                     supported interval.
     * @return the newly created subscription.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    UaSubscription createSubscription(int timeDeadband) throws OPCUAException;

    /**
     * Delete an existing subscription.
     * @param subscription The subscription to delete along with all contained MonitoredItems.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    void deleteSubscription(UaSubscription subscription) throws OPCUAException;

    /**
     * Add a list of item definitions as monitored items to a subscription.
     * @param subscription         The subscription to add the monitored items to
     * @param definitions          the {@link cern.c2mon.daq.opcua.mapping.ItemDefinition}s for which to create
     *                             monitored items
     * @param itemCreationCallback the callback function to execute when each item has been created successfully
     * @return a list of monitored items corresponding to the item definitions
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    List<UaMonitoredItem> subscribeItem(UaSubscription subscription, List<ItemDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCUAException;

    /**
     * Delete a monitored item from an OPC UA subscription.
     * @param clientHandle the identifier of the monitored item to remove.
     * @param subscription the subscription to remove the monitored item from.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws OPCUAException;

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link DataValue} containing the value read from the node.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    DataValue read(NodeId nodeId) throws OPCUAException;

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return a completable future  the {@link StatusCode} of the response to the write action
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    StatusCode write(NodeId nodeId, Object value) throws OPCUAException;

    /**
     * Call the method node with ID methodId contained in the object with ID objectId.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args     the input arguments to pass to the methodId call.
     * @return the StatusCode of the methodId response as key and the output arguments of the called method (if
     * applicable, else null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args) throws OPCUAException;

    /**
     * Fetches the node's first parent object node, if such a node exists.
     * @param nodeId the node whose parent to fetch
     * @return the parent node's NodeId
     * @throws OPCUAException of type {@link ConfigurationException} in case the nodeId is orphaned, or of types {@link
     *                        CommunicationException} or {@link LongLostConnectionException} as detailed in the
     *                        implementation class's JavaDoc.
     */
    NodeId getParentObjectNodeId(NodeId nodeId) throws OPCUAException;

    /**
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    ServerRedundancyTypeNode getServerRedundancyNode() throws OPCUAException;

    /**
     * Check whether a subscription is subscribed as part of the current session.
     * @param subscription the subscription to check for currentness.
     * @return true if the subscription is a valid in the current session.
     */
    boolean isCurrent(UaSubscription subscription);

}
