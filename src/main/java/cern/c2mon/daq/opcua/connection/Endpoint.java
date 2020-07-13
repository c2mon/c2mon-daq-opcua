package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collection;
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
     * @throws OPCUAException of type {@link CommunicationException} or {@link ConfigurationException}.
     */
    void initialize(String uri) throws OPCUAException;

    void manageSessionActivityListener(boolean add, SessionActivityListener listener);

    String getUri();

    /**
     * Disconnect from the server.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    void disconnect() throws OPCUAException;

    /**
     * Add a list of item definitions as monitored items to a subscription with a certain number of retries in case of
     * connection error and notify the endpointListener of future value updates
     * @param group The subscriptionGroup to add the definitions to
     * @param definitions        the {@link ItemDefinition}s for which to create monitored items
     * @return valid client handles
     */
    Map<UInteger, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) throws ConfigurationException;

    void recreateSubscription(UaSubscription subscription) throws OPCUAException;

    void recreateAllSubscriptions() throws CommunicationException;

    Map<UInteger, SourceDataTagQuality> subscribeWithCallback(int publishingInterval, Collection<ItemDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCUAException;
    /**
     * Delete a monitored item from an OPC UA subscription.
     * @param clientHandle    the identifier of the monitored item to remove.
     * @param publishInterval the publishInterval of the subscription to remove the monitored item from.
     * @return whether the monitored item could be removed successfully
     */
    boolean deleteItemFromSubscription(UInteger clientHandle, int publishInterval);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link SourceDataTagQualityCode} of the reading
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException;

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    boolean write(NodeId nodeId, Object value) throws OPCUAException;

    /**
     * Call the method node with ID methodId contained in the object with ID objectId.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param arg      the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<Boolean, Object[]> callMethod(NodeId objectId, NodeId methodId, Object arg) throws OPCUAException;

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

}
