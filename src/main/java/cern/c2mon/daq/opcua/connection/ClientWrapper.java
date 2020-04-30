package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
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
    Map.Entry<StatusCode, Object[]> callMethod(ItemDefinition definition, Object... args);
}
