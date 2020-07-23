package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Map;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static org.easymock.EasyMock.createMock;

@Getter
public class ExceptionTestEndpoint extends TestEndpoint {

    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);

    public ExceptionTestEndpoint(IMessageSender messageSender, TagSubscriptionReader mapper) {
        super(messageSender, mapper);
    }

    @Override
    public void initialize(String uri) throws CommunicationException {
        throw new CommunicationException(CONNECT);
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
    public boolean deleteItemFromSubscription(UInteger clientHandle, int publishInterval) {
        return false;
    }
}
