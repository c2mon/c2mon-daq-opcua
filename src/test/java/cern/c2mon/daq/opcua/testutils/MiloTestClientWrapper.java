package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.createMock;

@Getter
public class MiloTestClientWrapper implements MiloClientWrapper {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);

    @Override
    public void initialize () throws ExecutionException, InterruptedException {
    }

    @Override
    public void disconnect () {
    }

    @Override
    public UaSubscription createSubscription (int timeDeadband) {
        return subscription;
    }

    @Override
    public CompletableFuture<Void> deleteSubscription (UaSubscription subscription) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<UaMonitoredItem>> subscribeItemDefinitions (UaSubscription subscription, List<ItemDefinition> definitions, Deadband deadband, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        return CompletableFuture.completedFuture(Collections.nCopies(definitions.size(), monitoredItem));
    }

    @Override
    public CompletableFuture<List<DataValue>> read (NodeId nodeIds) {
        DataValue dataValue = new DataValue(StatusCode.GOOD);
        return CompletableFuture.completedFuture(Collections.singletonList(dataValue));
    }

    @Override
    public boolean isConnected () {
        return true;
    }

    public CompletableFuture<Void> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) {
        return CompletableFuture.completedFuture(null);
    }
}
