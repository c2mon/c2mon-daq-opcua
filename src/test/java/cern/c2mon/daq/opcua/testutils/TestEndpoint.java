package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.createMock;

@Getter
@Setter
@Component(value = "testEndpoint")
public class TestEndpoint implements Endpoint {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);
    private boolean returnGoodStatusCodes = true;

    @Override
    public void initialize(String uri) throws CommunicationException {

    }

    @Override
    public CompletableFuture<Void> disconnect () {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public CompletableFuture<UaSubscription> createSubscription (int timeDeadband) {
        return CompletableFuture.completedFuture(subscription);
    }

    @Override
    public CompletableFuture<UaSubscription> deleteSubscription (UaSubscription subscription) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<UaMonitoredItem>> subscribeItem(UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(() -> itemCreationCallback.accept(monitoredItem, 1), 100, TimeUnit.MILLISECONDS);
        return CompletableFuture.completedFuture(Collections.nCopies(definitions.size(), monitoredItem));
    }

    @Override
    public boolean containsSubscription(UaSubscription subscription) {
        return false;
    }

    @Override
    public CompletableFuture<DataValue> read (NodeId nodeId) {
        StatusCode code = returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
        return CompletableFuture.completedFuture(new DataValue(new Variant(0), code));
    }

    @Override
    public CompletableFuture<StatusCode> write (NodeId nodeId, Object value) {
        return CompletableFuture.completedFuture(returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD);
    }

    @Override
    public CompletableFuture<NodeId> getParentObjectNodeId(NodeId nodeId) {
        return CompletableFuture.completedFuture(nodeId);
    }

    @Override
    public CompletableFuture<Map.Entry<StatusCode, Object[]>> callMethod(NodeId objectId, NodeId methodId, Object... args) {
        final StatusCode statusCode = returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
        return CompletableFuture.completedFuture(Map.entry(statusCode, args));
    }

    @Override
    public CompletableFuture<List<StatusCode>> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) {
        return CompletableFuture.completedFuture(null);
    }
}
