package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * An interface in between the C2MON OPC UA DAQ and the Eclipse Milo OPC UA client stack.
 */
public interface Endpoint {

    /**
     * Connect to a server through OPC. Discover available endpoints and select the most secure one in line with configuration options.
     * @param uri the server address to connect to
     * @throws CommunicationException if an execution exception occurs either during endpoint discovery, or when creating the client.
     */
    void initialize(String uri) throws CommunicationException, ConfigurationException;

    /**
     * Triggers a disconnect from the server
     * @return A CompletableFuture that completes when the disconnection concludes.
     */
    CompletableFuture<Void> disconnect();

    /**
     * Check whether the client is currently connected to a server.
     * @return true if the session is active and the client is therefore connected to a server
     */
    boolean isConnected();

    /**
     * Create and return a new subscription.
     * @param timeDeadband The subscription's publishing interval in milliseconds. If 0, the Server will use the fastest supported interval.
     * @return the newly created subscription
     * @throws CommunicationException if an execution exception occurs on creating a subscription
     */
    CompletableFuture<UaSubscription> createSubscription(int timeDeadband);

    /**
     * Delete an existing subscription
     * @param subscription The subscription to delete
     * @throws CommunicationException if an execution exception occurs on deleting a subscription
     * @return
     */
    CompletableFuture<UaSubscription> deleteSubscription(UaSubscription subscription);

    /**
     * Add a list of item definitions as monitored items to a subscription
     * @param subscription The subscription to add the monitored items to
     * @param definitions the {@link cern.c2mon.daq.opcua.mapping.ItemDefinition}s for which to create monitored items
     * @param itemCreationCallback the callback function to execute when each item has been created successfully
     * @return a list of monitored items corresponding to the item definitions
     * @throws CommunicationException if an execution exception occurs on creating the monitored items
     */
    CompletableFuture<List<UaMonitoredItem>> subscribeItem(UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback);

    boolean containsSubscription(UaSubscription subscription);

    /**
     * Delete a monitored item from an OPC UA subscription
     * @param clientHandle the identifier of the monitored item to remove
     * @param subscription the subscription to remove the monitored item from.
     * @throws CommunicationException if an execution exception occurs on deleting an item from a subscription
     * @return
     */
    CompletableFuture<List<StatusCode>> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read
     * @return the {@link DataValue} returned by the OPC UA stack upon reading the node
     * @throws CommunicationException if an execution exception occurs on reading from the node
     */
    CompletableFuture<DataValue> read(NodeId nodeId);

    /**
     * Write a value to a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node to write to
     * @param value the value to write to the node
     * @return the {@link StatusCode} of the response to the write action
     * @throws CommunicationException if an execution exception occurs on writing to the node
     */
    CompletableFuture<StatusCode> write(NodeId nodeId, Object value);

    /**
     * Fetches the node's first parent object node, if such a node exists.
     * @param nodeId the node whose parent to fetch
     * @return the parent node's NodeId
     */
    CompletableFuture<NodeId> getParentObjectNodeId(NodeId nodeId);

    /**
     * Call the method Node with ID methodId contained in the object with ID objectId.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args the input arguments to pass to the methodId call.
     * @return A CompletableFuture containing a Map.Entry with the StatusCode of the methodId response as key, and the methodId's output arguments (if applicable)
     */
    CompletableFuture<Map.Entry<StatusCode, Object[]>> callMethod(NodeId objectId, NodeId methodId, Object... args);
}
