package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloMocker;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;

public abstract class ControllerTestBase {
    protected static String ADDRESS_PROTOCOL_TCP = "URI=opc.tcp://";
    protected static String ADDRESS_PROTOCOL_INVALID = "URI=http://";
    protected static String ADDRESS_BASE = "test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";


    protected static ISourceDataTag aliveTag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    protected static Map<Long, ISourceDataTag> sourceTags = generateSourceTags();

    TagSubscriptionMapper mapper;
    Controller controller;
    MiloMocker mocker;
    IEquipmentConfiguration config;

    public static void setupWithAddressAndAliveTag (IEquipmentConfiguration config, String address, boolean aliveWriterEnabled, ISourceDataTag aliveTag) {
        expect(config.getAddress()).andReturn(address + aliveWriterEnabled).anyTimes();
        expect(config.getSourceDataTag(1L)).andReturn(aliveTag).anyTimes();
        replay(config);
    }

    public static Map<Long, ISourceDataTag> generateSourceTags () {
        Map<Long, ISourceDataTag> sourceTags = new HashMap<>();
        sourceTags.put(2L, ServerTagFactory.RandomUnsignedInt32.createDataTag());
        sourceTags.put(3L, ServerTagFactory.RandomBoolean.createDataTag());
        return sourceTags;
    }

    @BeforeEach
    public void setup() throws ConfigurationException {
        config = TestUtils.createMockConfig();
        expect(config.getSourceDataTags()).andReturn(sourceTags).anyTimes();
        setupWithAddressAndAliveTag(config, ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, true, aliveTag);

        MiloTestClientWrapper wrapper = new MiloTestClientWrapper();
        mapper = new TagSubscriptionMapperImpl();
        mocker = new MiloMocker(wrapper, mapper);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());

        Endpoint endpoint = new EndpointImpl(wrapper, mapper, new EventPublisher());
        controller = new ControllerImpl(endpoint, new AliveWriter(endpoint));
    }

    @AfterEach
    public void teardown() {
        controller = null;
    }
}