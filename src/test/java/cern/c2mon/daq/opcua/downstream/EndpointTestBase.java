package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

public abstract class EndpointTestBase {

    protected Deadband deadband = Deadband.of(5,5f,(short) 5);
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

    protected void mockStatusCodesAndClientHandles (ISourceDataTag[] goodTags, ISourceDataTag[] badTags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        mockStatusCodeAndClientHandle(monitoredItem, StatusCode.GOOD, goodTags);
        mockStatusCodeAndClientHandle(monitoredItem, StatusCode.BAD, badTags);
        replay(monitoredItem);
    }

    protected void mockGoodStatusCodesAndClientHandles (ISourceDataTag... goodTags) {
        mockStatusCodesAndClientHandles(goodTags, new ISourceDataTag[]{});
    }

    protected void mockBadStatusCodesAndClientHandles (ISourceDataTag... badTags) {
        mockStatusCodesAndClientHandles(new ISourceDataTag[]{}, badTags);
    }

    protected void mockStatusCodeAndClientHandle (UaMonitoredItem monitoredItem, StatusCode code, ISourceDataTag... tags) {
        if(tags.length == 0) return;
        expect(monitoredItem.getStatusCode()).andReturn(code).times(tags.length);
        for (ISourceDataTag tag : tags) {
            UInteger clientHandle = mapper.getDefinition(tag).getClientHandle();
            expect(monitoredItem.getClientHandle()).andReturn(clientHandle).once();
        }
    }
}
