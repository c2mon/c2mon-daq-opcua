package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.createMock;

@Getter @Setter
public class TestEndpoint implements Endpoint {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);
    boolean returnGoodStatusCodes = true;
    boolean isConnected = true;

    @Override
    public void initialize(String uri) {

    }

    @Override
    public void addEndpointSubscriptionListener (EndpointSubscriptionListener listener) {

    }

    @Override
    public void disconnect () {
    }

    @Override
    public UaSubscription createSubscription (int timeDeadband) {
        return subscription;
    }

    @Override
    public void deleteSubscription (UaSubscription subscription) { }

    @Override
    public List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(() -> itemCreationCallback.accept(monitoredItem, 1), 100, TimeUnit.MILLISECONDS);
        return Collections.nCopies(definitions.size(), monitoredItem);
    }

    @Override
    public DataValue read (NodeId nodeIds) {
        StatusCode code = returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
        return new DataValue(new Variant(0), code);
    }

    @Override
    public void browseNode(String indent, NodeId browseRoot) {

    }

    @Override
    public StatusCode write (NodeId nodeId, Object value) {
        return returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
    }

    @Override
    public NodeId getParentObjectNodeId(NodeId nodeId) {
        return nodeId;
    }

    @Override
    public Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args) {
        final StatusCode statusCode = returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
        return Map.entry(statusCode, args);
    }

    @Override
    public boolean isConnected () {
        return isConnected;
    }

    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) { }
}
