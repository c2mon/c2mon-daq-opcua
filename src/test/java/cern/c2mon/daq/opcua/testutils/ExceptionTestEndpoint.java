package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
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

    public ExceptionTestEndpoint(EndpointListener endpointListener) {
        super(endpointListener);
    }

    @Override
    public void initialize(String uri) throws CommunicationException {
        throw new CommunicationException(CONNECT);
    }

    @Override
    public void deleteSubscription(int publishInterval) throws OPCUAException {
        throw new CommunicationException(DELETE_SUBSCRIPTION);
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribeToGroup(int publishingInterval, Collection<ItemDefinition> definitions) throws CommunicationException {
        throw new CommunicationException(CREATE_MONITORED_ITEM);
    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws CommunicationException {
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
