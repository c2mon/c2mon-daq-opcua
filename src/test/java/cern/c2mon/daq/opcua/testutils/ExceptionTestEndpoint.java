package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static org.easymock.EasyMock.createMock;

@Getter
public class ExceptionTestEndpoint extends TestEndpoint {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);

    @Override
    public void initialize(String uri) throws CommunicationException {
        throw new CommunicationException(CONNECT);
    }

    @Override
    public CompletableFuture<UaSubscription> createSubscription (int timeDeadband) {
        final CompletableFuture<UaSubscription> f = new CompletableFuture<>();
        f.completeExceptionally(new CommunicationException(CREATE_SUBSCRIPTION));
        return f;
    }

    @Override
    public CompletableFuture<UaSubscription> deleteSubscription (UaSubscription subscription) {
        final CompletableFuture<UaSubscription> f = new CompletableFuture<>();
        f.completeExceptionally(new CommunicationException(DELETE_SUBSCRIPTION));
        return f;
    }

    @Override
    public CompletableFuture<List<UaMonitoredItem>> subscribeItem(UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        final CompletableFuture<List<UaMonitoredItem>> f = new CompletableFuture<>();
        f.completeExceptionally(new CommunicationException(CREATE_MONITORED_ITEM));
        return f;
    }

    @Override
    public CompletableFuture<DataValue> read (NodeId nodeId) {
        final CompletableFuture<DataValue> f = new CompletableFuture<>();
        f.completeExceptionally(new CommunicationException(READ));
        return f;
    }

    @Override
    public CompletableFuture<StatusCode> write (NodeId nodeId, Object value) {
        final CompletableFuture<StatusCode> f = new CompletableFuture<>();
        f.completeExceptionally(new CommunicationException(WRITE));
        return f;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    public CompletableFuture<List<StatusCode>> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) {
        final CompletableFuture<List<StatusCode>> f = new CompletableFuture<>();
        f.completeExceptionally(new CommunicationException(DELETE_MONITORED_ITEM));
        return f;
    }
}
