package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import static org.easymock.EasyMock.*;

public class MiloMocker {

    TestEndpoint client;
    TagSubscriptionMapper mapper;

    public MiloMocker(Endpoint client, TagSubscriptionMapper mapper) {
        assert client instanceof TestEndpoint;
        this.client = (TestEndpoint) client;
        this.mapper = mapper;
    }

    public void replay (){
        EasyMock.replay(client.getMonitoredItem());
    }

    public void mockStatusCodeAndClientHandle(StatusCode code, Collection<ISourceDataTag> tags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        expect(monitoredItem.getStatusCode()).andReturn(code).anyTimes();
        mockClientHandle(monitoredItem, tags);
    }

    public void mockStatusCodeAndClientHandle(StatusCode code, ISourceDataTag... tags) {
        mockStatusCodeAndClientHandle(code, Arrays.asList(tags));
    }

    public void mockGoodAndBadStatusCodesAndReplay(ISourceDataTag[] goodTags, ISourceDataTag[] badTags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.GOOD).times(goodTags.length);
        mockClientHandle(monitoredItem, Arrays.asList(goodTags));

        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.BAD).times(badTags.length);
        mockClientHandle(monitoredItem, Arrays.asList(badTags));
        replay();
    }

    public void mockValueConsumerAndSubscribeTagAndReplay(StatusCode statusCode, ISourceDataTag tag) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        UInteger clientHandle = mapper.getOrCreateDefinition(tag).getClientHandle();
        expect(monitoredItem.getClientHandle()).andReturn(clientHandle).anyTimes();

        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.GOOD).once(); // subscription callback
        expect(monitoredItem.getStatusCode()).andReturn(statusCode).once(); // item creation callback

        Capture<Consumer<DataValue>> capturedConsumer = newCapture();
        monitoredItem.setValueConsumer(capture(capturedConsumer));
        expectLastCall().andAnswer(() -> {
            Consumer<DataValue> consumer = capturedConsumer.getValue();
            consumer.accept(new DataValue(statusCode));
            return consumer;
        });
        replay();
    }

    protected void mockClientHandle (UaMonitoredItem monitoredItem, Collection<ISourceDataTag> tags) {
        if(tags.size() == 0) return;
        for (ISourceDataTag tag : tags) {
            UInteger clientHandle = mapper.getOrCreateDefinition(tag).getClientHandle();
            expect(monitoredItem.getClientHandle()).andReturn(clientHandle).once();
        }
    }
}