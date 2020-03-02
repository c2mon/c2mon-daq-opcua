package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.easymock.EasyMock.*;

public abstract class EndpointControllerTestBase {
    protected static String ADDRESS_STRING_BASE = "URI=opc.tcp://test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";
    protected static ISourceDataTag aliveTag = ServerTagFactory.RandomUnsignedInt32.createDataTag();

    IEquipmentMessageSender sender = createMock(IEquipmentMessageSender.class);
    OpcUaClient client = createMock(OpcUaClient.class);
    IEquipmentConfiguration config;

    @BeforeEach
    public void setup() {
        config = createMock(IEquipmentConfiguration.class);
        expect(config.getAliveTagId()).andReturn(1L).anyTimes();
        expect(config.getAliveTagInterval()).andReturn(13L).anyTimes();
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
