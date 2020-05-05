package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.createMock;

@Getter
public class ExceptionTestEndpoint extends TestEndpoint {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);

    @Override
    public void initialize(String uri) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.CONNECT);
    }

    @Override
    public UaSubscription createSubscription (int timeDeadband) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.CREATE_SUBSCRIPTION);
    }

    @Override
    public void deleteSubscription (UaSubscription subscription) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.DELETE_SUBSCRIPTION);}

    @Override
    public List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.SUBSCRIBE_TAGS);
    }

    @Override
    public DataValue read (NodeId nodeIds) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.READ);
    }

    @Override
    public StatusCode write (NodeId nodeId, Object value) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.WRITE);
    }

    @Override
    public boolean isConnected () {
        return false;
    }

    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) {
        throw new OPCCommunicationException(OPCCommunicationException.Context.DELETE_MONITORED_ITEM);
    }
}
