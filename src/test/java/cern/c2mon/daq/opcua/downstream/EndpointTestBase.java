package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.easymock.Capture;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.easymock.EasyMock.*;

public abstract class EndpointTestBase {

    protected Deadband deadband = Deadband.of(800,5f,(short) 5);
    protected ISourceDataTag tag1 = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = ServerTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = ServerTagFactory.RandomBoolean.createDataTag(deadband);

    TagSubscriptionMapper mapper;
    EventPublisher publisher;
    Endpoint endpoint;
    MiloTestClientWrapper client;

    @BeforeEach
    public void setup() {
        mapper =  new TagSubscriptionMapperImpl();
        client = new MiloTestClientWrapper();
        publisher = new EventPublisher();
        endpoint = new EndpointImpl(client, mapper, publisher);
    }

    protected void subscribeTag (ISourceDataTag tag) {
        endpoint.subscribeTag(tag);
    }

    protected void subscribeTags (ISourceDataTag... tags) throws ConfigurationException {
        endpoint.subscribeTags(Arrays.asList(tags));
    }

    protected void mockStatusCode (StatusCode code, ISourceDataTag... tags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        expect(monitoredItem.getStatusCode()).andReturn(code).anyTimes();
        mockClientHandle(monitoredItem, tags);
        replay(monitoredItem);
    }

    protected void mockGoodAndBadStatusCodes (ISourceDataTag[] goodTags, ISourceDataTag[] badTags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.GOOD).times(goodTags.length);
        mockClientHandle(monitoredItem, goodTags);

        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.BAD).times(badTags.length);
        mockClientHandle(monitoredItem, badTags);
        replay(monitoredItem);
    }

    protected void mockClientHandle (UaMonitoredItem monitoredItem, ISourceDataTag... tags) {
        if(tags.length == 0) return;
        for (ISourceDataTag tag : tags) {
            UInteger clientHandle = mapper.getDefinition(tag).getClientHandle();
            expect(monitoredItem.getClientHandle()).andReturn(clientHandle).once();
        }
    }

    protected void mockValueConsumerAndSubscribeTag(StatusCode statusCode, ISourceDataTag tag) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        UInteger clientHandle = mapper.getDefinition(tag).getClientHandle();
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
        replay(monitoredItem);


    }
}
