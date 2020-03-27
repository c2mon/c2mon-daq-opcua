package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * We have a one-to-one mapping in between Endpoint and Server. We cannot trust the server to handle concurrency properly.
 * Therefore, let's keep things synchronous.
 */
public interface MiloClientWrapper {
    void initialize() throws OPCCommunicationException;
    void connect() throws OPCCommunicationException;
    void disconnect() throws OPCCommunicationException;
    boolean isConnected() throws OPCCommunicationException;
    void addEndpointSubscriptionListener(EndpointSubscriptionListener listener);


    UaSubscription createSubscription(int timeDeadband) throws OPCCommunicationException;
    void deleteSubscription(UaSubscription subscription) throws OPCCommunicationException;

    void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws OPCCommunicationException;
    List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription,
                                                                       List<DataTagDefinition> definitions,
                                                                       Deadband deadband,
                                                                       BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCCommunicationException;
    List<DataValue> read(NodeId nodeIds)  throws OPCCommunicationException;
    void browseNode(String indent, NodeId browseRoot);
    StatusCode write(NodeId nodeId, Object value)  throws OPCCommunicationException;

}
