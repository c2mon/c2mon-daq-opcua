package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.messaging.IProcessMessageSender;
import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import org.easymock.Capture;

import static org.easymock.EasyMock.*;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class TestUtils {
    public static IEquipmentConfiguration createMockConfig() {
        IEquipmentConfiguration config = createMock(IEquipmentConfiguration.class);
        expect(config.getAliveTagId()).andReturn(1L).anyTimes();
        expect(config.getAliveTagInterval()).andReturn(13L).anyTimes();
        return config;
    }

    public static AppConfig createDefaultConfig() {
        return AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .enableInsecureCommunication(true)
                .enableOnDemandCertification(true)
                .keystore(AppConfig.KeystoreConfig.builder().build())
                .build();
    }

    public static class CommfaultSenderCapture {
        Capture<Long> id = newCapture();
        Capture<String> tagName = newCapture();
        Capture<Boolean> val = newCapture();
        Capture<String> msg= newCapture();

        public CommfaultSenderCapture(IProcessMessageSender sender) {
            sender.sendCommfaultTag(captureLong(id), capture(tagName), captureBoolean(val), capture(msg));
            expectLastCall().once();
            replay(sender);
        }

        public void verifyCapture(Long id, String tagName, String msg) {
            assertEquals(id, this.id.getValue().longValue());
            assertEquals(tagName, this.tagName.getValue());
            assertEquals(msg, this.msg.getValue());
        }
    }
}
