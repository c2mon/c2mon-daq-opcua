package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.messaging.IProcessMessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.process.EquipmentConfiguration;
import com.google.common.collect.ImmutableMap;
import org.easymock.Capture;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class TestUtils {

    public final static int TIMEOUT = 3000;
    public final static int TIMEOUT_IT = 6000;
    public final static int TIMEOUT_TOXI = 25;
    public final static int TIMEOUT_REDUNDANCY = 2;

    public static AppConfigProperties createDefaultConfig() {
        final ImmutableMap<String, Integer> certificationPriority = ImmutableMap.<String, Integer>builder()
                .put("none", 3).put("generate", 2).put("load", 1).build();
        return AppConfigProperties.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .queueSize(0)
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .requestTimeout(5000)
                .certificationPriority(certificationPriority)
                .trustAllServers(true)
                .keystore(new AppConfigProperties.KeystoreConfig("PKCS12", "", "password", "1"))
                .pkiConfig(new AppConfigProperties.PKIConfig("", ""))
                .maxRetryAttempts(1)
                .retryDelay(2000)
                .aliveWriterEnabled(false)
                .build();
    }

    public static class CommfaultSenderCapture {
        final Capture<Long> id = newCapture();
        final Capture<String> tagName = newCapture();
        final Capture<Boolean> val = newCapture();
        final Capture<String> msg= newCapture();

        public CommfaultSenderCapture(IProcessMessageSender sender) {
            sender.sendCommfaultTag(captureLong(id), capture(tagName), captureBoolean(val), capture(msg));
            expectLastCall().once();
        }

        public void verifyCapture(Long id, String tagName, String msg) {
            assertEquals(id, this.id.getValue().longValue());
            assertEquals(tagName, this.tagName.getValue());
            assertEquals(msg, this.msg.getValue());
        }
    }

    public static TestControllerProxy getFailoverProxy(Endpoint endpoint, IMessageSender messageSender) {
        final AppConfigProperties config = createDefaultConfig();
        final TestControllerProxy proxy = new TestControllerProxy(null, config, messageSender, endpoint);
        try {
            proxy.connect(Collections.singleton("test"));
        } catch (OPCUAException ignored) { }
        return proxy;
    }

    // Work-around for non-fixed ports in testcontainers, since GenericMessageHandlerTest required ports to be included in the configuration
    public static void mapEquipmentAddress(EquipmentConfiguration equipmentConfiguration, GenericContainer image) {
        final String hostRegex = ":(.[^/][^;]*)";
        final String equipmentAddress = equipmentConfiguration.getEquipmentAddress();

        final Matcher matcher = Pattern.compile(hostRegex).matcher(equipmentAddress);
        if (matcher.find()) {
            final String port = matcher.group(1);
            final String mappedAddress = equipmentAddress.replace(port + "", image.getMappedPort(Integer.parseInt(port)) + "");
            ReflectionTestUtils.setField(equipmentConfiguration, "equipmentAddress", mappedAddress);
        }
    }
}
