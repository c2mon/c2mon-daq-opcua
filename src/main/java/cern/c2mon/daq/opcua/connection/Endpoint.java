package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * We have a one-to-one mapping in between Endpoint and Server. We cannot trust the server to handle concurrency properly.
 * Therefore, let's keep things synchronous.
 */
public interface Endpoint {
    void initialize(String uri);
    void disconnect();
    boolean isConnected();
    void addEndpointSubscriptionListener(EndpointSubscriptionListener listener);


    UaSubscription createSubscription(int timeDeadband);
    void deleteSubscription(UaSubscription subscription);

    void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription);
    List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription,
                                                                       List<DataTagDefinition> definitions,
                                                                       BiConsumer<UaMonitoredItem, Integer> itemCreationCallback);
    DataValue read(NodeId nodeIds) ;
    void browseNode(String indent, NodeId browseRoot);
    StatusCode write(NodeId nodeId, Object value);


    /**
     * Fetches the node's first parent object node, if such a node exists. An @{@link OPCCommunicationException} is
     * thrown if no such object node is found, or an error occurs during browsing.
     * @param nodeId the node whose parent to fetch
     * @return the parent node's NodeId
     */
    NodeId getParentObjectNodeId(NodeId nodeId);

    /***
     * Call the method Node with ID methodId contained in the object with ID objectId.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args the input arguments to pass to the methodId call.
     * @return A Map.Entry containing the StatusCode of the methodId response as key, and the methodId's output arguments (if applicable)
     */
    Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args);
}
