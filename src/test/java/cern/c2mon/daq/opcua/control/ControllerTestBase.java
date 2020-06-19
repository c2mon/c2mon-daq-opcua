package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.*;
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
    protected static String ADDRESS_BASE = "test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";

    protected ISourceDataTag tag1 = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = EdgeTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = EdgeTagFactory.AlternatingBoolean.createDataTag(5f, (short)2, 800);



    protected static Map<Long, ISourceDataTag> sourceTags = generateSourceTags();
    protected static String uri;

    TagSubscriptionMapper mapper;
    TestEndpoint endpoint;
    TestListeners.TestListener listener;
    Controller controller;
    MiloMocker mocker;


    public static Map<Long, ISourceDataTag> generateSourceTags () {
        Map<Long, ISourceDataTag> sourceTags = new HashMap<>();
        sourceTags.put(2L, EdgeTagFactory.RandomUnsignedInt32.createDataTag());
        sourceTags.put(3L, EdgeTagFactory.AlternatingBoolean.createDataTag());
        return sourceTags;
    }

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        uri = ADDRESS_PROTOCOL_TCP + ADDRESS_BASE + true;
        endpoint = new TestEndpoint();
        mapper = new TagSubscriptionMapperImpl();
        listener = new TestListeners.TestListener(mapper);
        controller = new ControllerImpl(mapper, listener, TestUtils.getFailoverProxy(endpoint));
        mocker = new MiloMocker(endpoint, mapper);
    }

    @AfterEach
    public void teardown() {
        controller = null;
    }

    @SneakyThrows
    protected void subscribeTagsAndMockStatusCode (StatusCode code, ISourceDataTag... tags)  {
        mocker.mockStatusCodeAndClientHandle(code, tags);
        mocker.replay();
        controller.subscribeTags(Arrays.asList(tags));
    }

    protected boolean isSubscribed(ISourceDataTag tag) {
        final SubscriptionGroup group = mapper.getGroup(tag.getTimeDeadband());
        return group != null && group.contains(tag);
    }
}
