package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TagHandlerTestBase {
    protected static String ADDRESS_PROTOCOL_TCP = "URI=opc.tcp://";
    protected static String ADDRESS_BASE = "test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";

    protected ISourceDataTag tag1 = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = EdgeTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = EdgeTagFactory.AlternatingBoolean.createDataTag(5f, (short)2, 800);




    protected Map<Long, ISourceDataTag> sourceTags;
    protected String uri;

    TagSubscriptionMapper mapper;
    TestEndpoint endpoint;
    TestListeners.TestListener listener;
    IDataTagHandler tagHandler;
    MiloMocker mocker;
    TestControllerProxy proxy;

    ISourceDataTag tagInSource1 = EdgeTagFactory.RandomUnsignedInt32.createDataTagWithID(10L);
    ISourceDataTag tagInSource2 = EdgeTagFactory.AlternatingBoolean.createDataTagWithID(20L);

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {

        uri = ADDRESS_PROTOCOL_TCP + ADDRESS_BASE + true;
        sourceTags = new ConcurrentHashMap<>();
        sourceTags.put(10L, tagInSource1);
        sourceTags.put(20L, tagInSource2);

        mapper = new TagSubscriptionMapper();
        listener = new TestListeners.TestListener();
        endpoint = new TestEndpoint(listener, mapper);
        proxy = TestUtils.getFailoverProxy(endpoint, listener);
        tagHandler = new DataTagHandler(mapper, listener, proxy);
        mocker = new MiloMocker(endpoint, mapper);
    }

    @AfterEach
    public void teardown() {
        tagHandler = null;
    }

    protected void subscribeTagsAndMockStatusCode (StatusCode code, ISourceDataTag... tags) throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(code, tags);
        mocker.replay();
        tagHandler.subscribeTags(Arrays.asList(tags));
    }

    protected boolean isSubscribed(ISourceDataTag tag) {
        final SubscriptionGroup group = mapper.getGroup(tag.getTimeDeadband());
        return group != null && group.contains(tag.getId());
    }
}
