package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.createMock;

@Getter @Setter
public class MiloTestClientWrapper implements MiloClientWrapper {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);
    boolean returnGoodStatusCodes = true;

    @Override
    public void initialize () {
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
    public List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription, List<DataTagDefinition> definitions, Deadband deadband, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(() -> itemCreationCallback.accept(monitoredItem, 1), 100, TimeUnit.MILLISECONDS);
        return Collections.nCopies(definitions.size(), monitoredItem);
    }

    @Override
    public List<DataValue> read (NodeId nodeIds) {
        StatusCode code = returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
        return Collections.singletonList(new DataValue(code));
    }

    @Override
    public void browseNode(String indent, NodeId browseRoot) {

    }

    @Override
    public StatusCode write (NodeId nodeId, Object value) {
        return StatusCode.GOOD;
    }

    @Override
    public boolean isConnected () {
        return true;
    }

    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) { }
}
