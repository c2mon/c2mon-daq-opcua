package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloClientWrapperTest;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
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
    Endpoint endpoint;
    MiloClientWrapperTest client;

    @BeforeEach
    public void setup() {
        mapper =  new TagSubscriptionMapperImpl();
        client = new MiloClientWrapperTest();
        endpoint = new EndpointImpl(mapper, client);
    }

    protected void subscribeTags (ISourceDataTag... tags) {
        endpoint.subscribeTags(Arrays.asList(tags));
    }

    protected void mockGoodSubscriptionStatusCode (ISourceDataTag... dataTags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();

        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.GOOD).times(dataTags.length);
        for (ISourceDataTag tag : dataTags) {
            UInteger clientHandle = mapper.getDefinition(tag).getClientHandle();
            expect(monitoredItem.getClientHandle()).andReturn(clientHandle).once();
        }
        replay(monitoredItem);
    }
}
