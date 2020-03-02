package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public interface MiloClientWrapper {
    void initialize() throws ExecutionException, InterruptedException;
    void disconnect();
    boolean isConnected();

    UaSubscription createSubscription(int timeDeadband);
    CompletableFuture<Void> deleteSubscription(UaSubscription subscription);

    CompletableFuture<Void> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription);
    CompletableFuture<List<UaMonitoredItem>> subscribeItemDefinitions (UaSubscription subscription,
                                                                       List<ItemDefinition> definitions,
                                                                       Deadband deadband,
                                                                       BiConsumer<UaMonitoredItem, Integer> itemCreationCallback);
    CompletableFuture<List<DataValue>> read(NodeId nodeIds);

}
