package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
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
    public UaSubscription createSubscription (int timeDeadband) throws CommunicationException {
        throw new CommunicationException(CREATE_SUBSCRIPTION);
    }

    @Override
    public void deleteSubscription (UaSubscription subscription) throws CommunicationException {
        throw new CommunicationException(DELETE_SUBSCRIPTION);
    }

    @Override
    public List<UaMonitoredItem> subscribeItem(UaSubscription subscription, List<ItemDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws CommunicationException {
        throw new CommunicationException(CREATE_MONITORED_ITEM);
    }

    @Override
    public DataValue read (NodeId nodeId) throws CommunicationException {
        throw new CommunicationException(READ);
    }

    @Override
    public StatusCode write (NodeId nodeId, Object value) throws CommunicationException {
        throw new CommunicationException(WRITE);
    }


    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws CommunicationException {
        throw new CommunicationException(DELETE_MONITORED_ITEM);
    }
}
