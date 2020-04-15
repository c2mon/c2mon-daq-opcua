package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloMocker;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

public abstract class EndpointTestBase {

    protected Deadband deadband = Deadband.of(800,5f,(short) 5);
    protected ISourceDataTag tag1 = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = ServerTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = ServerTagFactory.RandomBoolean.createDataTag(deadband);

    TagSubscriptionMapper mapper;
    EventPublisher publisher;
    Endpoint endpoint;
    MiloTestClientWrapper client;
    MiloMocker mocker;

    @BeforeEach
    public void setup() {
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
        mocker.mockStatusCode(code, tags);
        mocker.replay();
        subscribeTags(tags);
    }
}
