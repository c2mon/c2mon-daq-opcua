package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloMocker;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class ControllerTestBase {
    protected static String ADDRESS_PROTOCOL_TCP = "URI=opc.tcp://";
    protected static String ADDRESS_PROTOCOL_INVALID = "URI=http://";
    protected static String ADDRESS_BASE = "test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";

    protected ISourceDataTag tag1 = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = ServerTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = ServerTagFactory.AlternatingBoolean.createDataTag(5f, (short)2, 800);



    protected static Map<Long, ISourceDataTag> sourceTags = generateSourceTags();
    protected static String uri;

    TagSubscriptionMapper mapper;
    TestEndpoint endpoint;
    ServerTestListener.TestListener listener;
    Controller controller;
    MiloMocker mocker;


    public static Map<Long, ISourceDataTag> generateSourceTags () {
        Map<Long, ISourceDataTag> sourceTags = new HashMap<>();
        sourceTags.put(2L, ServerTagFactory.RandomUnsignedInt32.createDataTag());
        sourceTags.put(3L, ServerTagFactory.AlternatingBoolean.createDataTag());
        return sourceTags;
    }

    @BeforeEach
    public void setUp() {
        uri = ADDRESS_PROTOCOL_TCP + ADDRESS_BASE + true;
        endpoint = new TestEndpoint();
        mapper = new TagSubscriptionMapperImpl();
        listener = new ServerTestListener.TestListener();
        controller = new ControllerImpl(endpoint, mapper, listener);
        mocker = new MiloMocker(endpoint, mapper);
    }

    @AfterEach
    public void teardown() {
        controller = null;
    }


    protected void subscribeTags (ISourceDataTag... tags) throws ConfigurationException {
        controller.subscribeTags(Arrays.asList(tags));
    }

    @SneakyThrows
    protected void subscribeTagsAndMockStatusCode (StatusCode code, ISourceDataTag... tags)  {
        mocker.mockStatusCodeAndClientHandle(code, tags);
        mocker.replay();
        subscribeTags(tags);
    }
}
