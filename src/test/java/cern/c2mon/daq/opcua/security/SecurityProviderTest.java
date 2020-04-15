package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.configuration.AuthConfig;
import cern.c2mon.daq.opcua.exceptions.CertificateBuilderException;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityProviderTest {

    List<MessageSecurityMode> modes;
    List<SecurityPolicy> policies;

    SecurityProvider p = new SecurityProvider();
    AppConfig config;


    @BeforeEach
    public void setUp() {
        config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .build();
        AuthConfig auth = AuthConfig.builder()
                .keyStorePath("PKI/x.pem")
                .keyStorePassword("pwd")
                .keyStoreAlias("alias")
                .build();
        config.setAuth(auth);

        SelfSignedCertifier ssc = new SelfSignedCertifier();
        ssc.setConfig(config);
        p.setConfig(config);

        p.setSelfSigned(ssc);
        p.setNoSecurity(new NoSecurityCertifier());

        modes = new ArrayList<>(Arrays.asList(MessageSecurityMode.SignAndEncrypt, MessageSecurityMode.Sign, MessageSecurityMode.None));
        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));

    }

    @Test
    public void certifyBuilderWithSecurityShouldAppendKeyPairAndCertificate() throws CertificateBuilderException {
        modes.remove(MessageSecurityMode.None);
        policies.remove(SecurityPolicy.None);
        for (SecurityPolicy policy : policies) {
            for (MessageSecurityMode m : modes) {
                OpcUaClientConfig config = certifyWith(policy, m);
                assertNotNull(config.getKeyPair());
                assertNotNull(config.getCertificate());
            }
        }
    }

    @Test
    public void certifyBuilderWithoutSecurityPolicyShouldNotChangeBuilder() throws CertificateBuilderException {
        OpcUaClientConfig expected = OpcUaClientConfig.builder().build();
        for (MessageSecurityMode m : modes) {
            OpcUaClientConfig actual = certifyWith(SecurityPolicy.None, m);
            compareConfig(expected, actual);
        }
    }

    @Test
    public void certifyBuilderWithModeNoneShouldNotChangeBuilder() throws CertificateBuilderException {
        OpcUaClientConfig expected = OpcUaClientConfig.builder().build();
        for (SecurityPolicy policy : policies) {
            OpcUaClientConfig actual = certifyWith(policy, MessageSecurityMode.None);
            compareConfig(expected, actual);
        }
    }

    private void compareConfig(OpcUaClientConfig a, OpcUaClientConfig b) {
        assertEquals(a.getApplicationName(), b.getApplicationName());
        assertEquals(a.getCertificate(), b.getCertificate());
        assertEquals(a.getCertificateChain(), b.getCertificateChain());
        assertEquals(a.getCertificateValidator().getClass(), b.getCertificateValidator().getClass());
        assertEquals(a.getKeyPair(), b.getKeyPair());
    }

    private OpcUaClientConfig certifyWith(SecurityPolicy sp, MessageSecurityMode mode) throws CertificateBuilderException {
        OpcUaClientConfigBuilder builder = OpcUaClientConfig.builder();
        p.certifyBuilder(builder, sp, mode);
        return builder.build();
    }


}