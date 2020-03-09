package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.downstream.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapper;
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
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.createMock;

@Getter
public class MiloTestClientWrapper implements MiloClientWrapper {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);

    @Override
    public void initialize () {
    }

    @Override
    public void connect () {

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
    public List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription, List<ItemDefinition> definitions, Deadband deadband, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        return Collections.nCopies(definitions.size(), monitoredItem);
    }

    @Override
    public List<DataValue> read (NodeId nodeIds) {
        DataValue dataValue = new DataValue(StatusCode.GOOD);
        return Collections.singletonList(dataValue);
    }

    @Override
    public StatusCode write (NodeId nodeId, DataValue value) {
        return StatusCode.GOOD;
    }

    @Override
    public boolean isConnected () {
        return true;
    }

    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) { }
}
