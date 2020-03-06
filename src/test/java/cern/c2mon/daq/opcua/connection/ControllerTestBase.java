package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapper;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.MiloTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;

public abstract class ControllerTestBase {
    protected static String ADDRESS_STRING_BASE = "URI=opc.tcp://test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";
    protected static ISourceDataTag aliveTag = ServerTagFactory.RandomUnsignedInt32.createDataTag();

    IEquipmentMessageSender sender = createMock(IEquipmentMessageSender.class);
    IEquipmentConfiguration config;
    MiloClientWrapper wrapper = new MiloTestClientWrapper();
    TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    Endpoint endpoint;

    @BeforeEach
    public void setup() throws ConfigurationException {
        config = createMock(IEquipmentConfiguration.class);
        expect(config.getAliveTagId()).andReturn(1L).anyTimes();
        expect(config.getAliveTagInterval()).andReturn(13L).anyTimes();

        Map<Long, ISourceDataTag> sourceTags = new HashMap<>();
        sourceTags.put(2L, ServerTagFactory.RandomUnsignedInt32.createDataTag());
        sourceTags.put(3L, ServerTagFactory.RandomBoolean.createDataTag());
        expect(config.getSourceDataTags()).andReturn(sourceTags).anyTimes();
    }

    @AfterEach
    public void teardown() {
        config = null;
    }

    protected void setupWithAliveTag (boolean aliveWriterEnabled, ISourceDataTag aliveTag) {
        expect(config.getAddress()).andReturn(ADDRESS_STRING_BASE + aliveWriterEnabled).anyTimes();
        expect(config.getSourceDataTag(1L)).andReturn(aliveTag).anyTimes();
        replay(config);
    }
}
