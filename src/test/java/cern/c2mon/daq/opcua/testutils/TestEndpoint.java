package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    public void disconnect () {}

    @Override
    public UaSubscription createSubscription (int timeDeadband) throws CommunicationException {
        return subscription;
    }

    @Override
    public void deleteSubscription (UaSubscription subscription) throws CommunicationException {
    }

    @Override
    public List<UaMonitoredItem> subscribeItem(UaSubscription subscription, List<ItemDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws CommunicationException {
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(() -> itemCreationCallback.accept(monitoredItem, 1), 100, TimeUnit.MILLISECONDS);
        return Collections.nCopies(definitions.size(), monitoredItem);
    }

    @Override
    public boolean isCurrent(UaSubscription subscription) {
        return false;
    }

    @Override
    public DataValue read (NodeId nodeId) throws CommunicationException {
        return new DataValue(new Variant(0), returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD);
    }

    @Override
    public StatusCode write (NodeId nodeId, Object value) throws CommunicationException {
        return returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD;
    }

    @Override
    public NodeId getParentObjectNodeId(NodeId nodeId) {
        return nodeId;
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() throws OPCUAException {
        return null;
    }

    @Override
    public Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args) {
        return Map.entry(returnGoodStatusCodes ? StatusCode.GOOD : StatusCode.BAD, args);
    }

    @Override
    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws CommunicationException {
    }
}
