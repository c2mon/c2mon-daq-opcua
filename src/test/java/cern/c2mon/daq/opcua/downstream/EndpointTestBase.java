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
import java.util.concurrent.ExecutionException;

import static org.easymock.EasyMock.expect;

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

    protected void subscribeTag (ISourceDataTag tag) throws ExecutionException, InterruptedException {
        endpoint.subscribeTag(tag).get();
    }

    protected void subscribeTags (ISourceDataTag... tags) throws ExecutionException, InterruptedException, ConfigurationException {
        endpoint.subscribeTags(Arrays.asList(tags)).get();
    }

    protected void mockSubscriptionStatusCode (UaMonitoredItem monitoredItem, StatusCode code, ISourceDataTag... tags) {
        if(tags.length == 0) return;
        expect(monitoredItem.getStatusCode()).andReturn(code).times(tags.length);
        for (ISourceDataTag tag : tags) {
            UInteger clientHandle = mapper.getDefinition(tag).getClientHandle();
            expect(monitoredItem.getClientHandle()).andReturn(clientHandle).once();
        }
    }
}
