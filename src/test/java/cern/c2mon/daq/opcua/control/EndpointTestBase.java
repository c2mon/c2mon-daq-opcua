package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloMocker;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

public abstract class EndpointTestBase {

    protected ISourceDataTag tag1 = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = ServerTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = ServerTagFactory.AlternatingBoolean.createDataTag(5f, (short)2, 800);

    TagSubscriptionMapper mapper;
    EventPublisher publisher;
    Endpoint endpoint;
    MiloTestClientWrapper client;
    MiloMocker mocker;

    @BeforeEach
    public void setUp() {
        mapper =  new TagSubscriptionMapperImpl();
        client = new MiloTestClientWrapper();
        publisher = new EventPublisher();
        endpoint = new EndpointImpl(client, mapper, publisher);
        mocker = new MiloMocker(client, mapper);
    }

    protected void subscribeTags (ISourceDataTag... tags) throws ConfigurationException {
        endpoint.subscribeTags(Arrays.asList(tags));
    }

    @SneakyThrows
    protected void subscribeTagsAndMockStatusCode (StatusCode code, ISourceDataTag... tags)  {
        mocker.mockStatusCodeAndClientHandle(code, tags);
        mocker.replay();
        subscribeTags(tags);
    }
}
