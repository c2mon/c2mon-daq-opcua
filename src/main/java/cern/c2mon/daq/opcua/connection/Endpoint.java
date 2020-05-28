package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
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
 * An interface in between the C2MON OPC UA DAQ and the OPC UA client stack.
 */
public interface Endpoint {

    /**
     * Connects to a server through OPC UA.
     * @param uri the server address to connect to
     * @throws ConfigurationException if it is not possible to connect to any of the the OPC UA server's endpoints with
     *                                the given authentication configuration settings
     * @throws CommunicationException if it is not possible to connect to the OPC UA server within the configured number
     *                                of attempts
     * @throws InterruptedException if the method was interrupted during initialization.
     */
    void initialize(String uri) throws OPCUAException, InterruptedException;

    /**
     * Triggers a disconnect from the server
     * @return A CompletableFuture that completes when the disconnection concludes.
     */
    CompletableFuture<Void> disconnect();

    /**
     * Create and return a new subscription.
     * @param timeDeadband The subscription's publishing interval in milliseconds. If 0, the Server will use the fastest supported interval.
     * @return a completable future containing the newly created subscription
     */
    CompletableFuture<UaSubscription> createSubscription(int timeDeadband);

    /**
     * Delete an existing subscription
     * @param subscription The subscription to delete
     * @return a completable future containing the deleted subscription
     */
    CompletableFuture<UaSubscription> deleteSubscription(UaSubscription subscription);

    /**
     * Add a list of item definitions as monitored items to a subscription
     * @param subscription The subscription to add the monitored items to
     * @param definitions the {@link cern.c2mon.daq.opcua.mapping.ItemDefinition}s for which to create monitored items
     * @param itemCreationCallback the callback function to execute when each item has been created successfully
     * @return a completable future containing a list of monitored items corresponding to the item definitions
     */
    CompletableFuture<List<UaMonitoredItem>> subscribeItem(UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback);

    /**
     * Delete a monitored item from an OPC UA subscription
     * @param clientHandle the identifier of the monitored item to remove
     * @param subscription the subscription to remove the monitored item from.
     * @return a completable future containing a list of StatusCodes for each deletion request
     */
    CompletableFuture<List<StatusCode>> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read
     * @return a completable future containing the {@link DataValue} returned by the OPC UA stack upon reading the node
     */
    CompletableFuture<DataValue> read(NodeId nodeId);

    /**
     * Write a value to a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node to write to
     * @param value the value to write to the node
     * @return a completable future  the {@link StatusCode} of the response to the write action
     */
    CompletableFuture<StatusCode> write(NodeId nodeId, Object value);

    /**
     * Call the method Node with ID methodId contained in the object with ID objectId.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args the input arguments to pass to the methodId call.
     * @return A CompletableFuture containing a Map.Entry with the StatusCode of the methodId response as key, and the methodId's output arguments (if applicable)
     */
    CompletableFuture<Map.Entry<StatusCode, Object[]>> callMethod(NodeId objectId, NodeId methodId, Object... args);

    /**
     * Fetches the node's first parent object node, if such a node exists.
     * @param nodeId the node whose parent to fetch
     * @return the parent node's NodeId
     */
    CompletableFuture<NodeId> getParentObjectNodeId(NodeId nodeId);

    /**
     * Return whether a subscription is part of the current session
     * @param subscription the subscription to check
     * @return true if the subscription is a valid in the current session
     */
    boolean isCurrent(UaSubscription subscription);

}
