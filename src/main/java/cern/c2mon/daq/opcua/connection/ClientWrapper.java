package cern.c2mon.daq.opcua.connection;

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
public interface ClientWrapper {
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
    /***
     * Called if a command tag does not contain a redundant item name. In this case it is assumed that the given item name
     * refers to the method node, and that the method node is not orphaned. In this case the first refered object node
     * in reverse browse direction is used in the method call as an object.
     * @param methodId the nodeId of class Method which shall be called
     * @param args the input arguments to pass to the method call.
     * @return A Map.Entry containing the StatusCode of the method response as key, and the method's output arguments (if applicable)
     */
    Map.Entry<StatusCode, Object[]> callMethod(NodeId methodId, Object... args);


    /***
     * Called if a command tag contains a redundant item name. In this case it is assumed that the primary item name
     * refers to the object node containing the methodId, and that the redundant item name refers to the method node.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args the input arguments to pass to the methodId call.
     * @return A Map.Entry containing the StatusCode of the methodId response as key, and the methodId's output arguments (if applicable)
     */
    Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args);
}
