package cern.c2mon.daq.opcua.tagHandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.testutils.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class TagHandlerTestBase {
    protected static String ADDRESS_PROTOCOL_TCP = "URI=opc.tcp://";
    protected static String ADDRESS_BASE = "test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";

    protected ISourceDataTag tag1 = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = EdgeTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = EdgeTagFactory.AlternatingBoolean.createDataTag(5f, (short)2, 800);



    protected static Map<Long, ISourceDataTag> sourceTags = generateSourceTags();
    protected static String uri;

    TagSubscriptionManager mapper;
    TestEndpoint endpoint;
    TestListeners.TestListener listener;
    IDataTagHandler controller;
    MiloMocker mocker;
    TestControllerProxy proxy;


    public static Map<Long, ISourceDataTag> generateSourceTags () {
        Map<Long, ISourceDataTag> sourceTags = new HashMap<>();
        sourceTags.put(2L, EdgeTagFactory.RandomUnsignedInt32.createDataTag());
        sourceTags.put(3L, EdgeTagFactory.AlternatingBoolean.createDataTag());
        return sourceTags;
    }

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        uri = ADDRESS_PROTOCOL_TCP + ADDRESS_BASE + true;
        mapper = new TagSubscriptionManager();
        listener = new TestListeners.TestListener();
        endpoint = new TestEndpoint(listener, mapper);
        proxy = TestUtils.getFailoverProxy(endpoint, listener);
        controller = new DataTagHandler(mapper, listener, proxy);
        mocker = new MiloMocker(endpoint, mapper);
    }

    @AfterEach
    public void teardown() {
        controller = null;
    }

    protected void subscribeTagsAndMockStatusCode (StatusCode code, ISourceDataTag... tags) throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(code, tags);
        mocker.replay();
        controller.subscribeTags(Arrays.asList(tags));
    }

    protected boolean isSubscribed(ISourceDataTag tag) {
        final SubscriptionGroup group = mapper.getGroup(tag.getTimeDeadband());
        return group != null && group.contains(tag);
    }
}
