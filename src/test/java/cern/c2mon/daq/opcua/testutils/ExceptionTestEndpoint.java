package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.TriConsumer;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collection;
import java.util.Map;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static org.easymock.EasyMock.createMock;

@Getter
public class ExceptionTestEndpoint extends TestEndpoint {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);

    @Override
    public void initialize(String uri, Collection<SessionActivityListener> listeners) throws CommunicationException {
        throw new CommunicationException(CONNECT);
    }

    @Override
    public void deleteSubscription(int publishInterval) throws OPCUAException {
        throw new CommunicationException(DELETE_SUBSCRIPTION);
    }

    @Override
    public Map<UInteger, SourceDataTagQualityCode> subscribeWithValueUpdateCallback(int publishingInterval, Collection<ItemDefinition> definitions, TriConsumer<UInteger, SourceDataTagQualityCode, ValueUpdate> onValueUpdate) throws CommunicationException {
        throw new CommunicationException(CREATE_MONITORED_ITEM);
    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQualityCode> read(NodeId nodeId) throws CommunicationException {
        throw new CommunicationException(READ);
    }

    @Override
    public boolean write(NodeId nodeId, Object value) throws CommunicationException {
        throw new CommunicationException(WRITE);
    }

    @Override
    public void deleteItemFromSubscription(UInteger clientHandle, int publishInterval) throws CommunicationException {
        throw new CommunicationException(DELETE_MONITORED_ITEM);
    }
}
