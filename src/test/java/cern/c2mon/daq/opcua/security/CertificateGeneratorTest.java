package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificateGeneratorTest {
    AppConfig config;
    CertificateGenerator generator;

    List<MessageSecurityMode> modes;
    List<SecurityPolicy> policies;

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
                .enableInsecureCommunication(false)
                .enableOnDemandCertification(true)
                .keystore(AppConfig.KeystoreConfig.builder().build())
                .build();
        generator = new CertificateGenerator(config);

        modes = new ArrayList<>(Arrays.asList(MessageSecurityMode.SignAndEncrypt, MessageSecurityMode.Sign, MessageSecurityMode.None));
        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));

    }
    @Test
    void certifyShouldModifyBuilderIfSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            generator.certify(actual, e);
            if (generator.canCertify(e)) {
                final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                        .setCertificate(generator.certificate)
                        .setKeyPair(generator.keyPair)
                        .setEndpoint(e);
                assertEqualConfigFields(expected, actual);
            }
        }
    }

    @Test
    void certifyShouldNotDoAnythingIfNotSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            generator.certify(actual, e);
            if (!generator.canCertify(e)) {
                assertEqualConfigFields(new OpcUaClientConfigBuilder(), actual);
            }
        }
    }

    @Test
    void onlyRsaSha265ShouldBeCertifiable() {
        for (SecurityPolicy p : policies) {
            final EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            boolean expected = p.getAsymmetricSignatureAlgorithm().getTransformation().equalsIgnoreCase("SHA256withRSA");
            assertEquals(expected, generator.canCertify(e));
        }
    }

    @Test
    void canCertifyWithBadConfigurationShouldReturnFalse() {
        CertificateGenerator badGenerator = new CertificateGenerator(AppConfig.builder().build());
        final EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        assertFalse(badGenerator.canCertify(e));
    }

    @Test
    void certifyWithMisconfiguredEndpointShouldNotCertify() {
        assertFalse(generator.canCertify(createEndpointWithSecurityPolicy("badUri")));
    }

    @Test
    void onlyRsaSha265ShouldBeSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            boolean expected = p.getAsymmetricSignatureAlgorithm().getTransformation().equalsIgnoreCase("SHA256withRSA");
            assertEquals(expected, generator.supportsAlgorithm(e));
        }
    }
    private EndpointDescription createEndpointWithSecurityPolicy(String securityPolicyUri) {
        return new EndpointDescription("test",
                null,
                null,
                null,
                securityPolicyUri,
                null,
                null,
                null);
    }

    public static void assertEqualConfigFields(OpcUaClientConfigBuilder expectedBuilder, OpcUaClientConfigBuilder actualBuilder) {
        final OpcUaClientConfig expected = expectedBuilder.build();
        final OpcUaClientConfig actual = actualBuilder.build();
        assertEquals(expected.getEndpoint(), actual.getEndpoint());
        assertEquals(expected.getCertificate(), actual.getCertificate());
        if (expected.getKeyPair().isPresent() && actual.getKeyPair().isPresent()) {
            assertEquals(expected.getKeyPair().get().getPrivate().toString(), actual.getKeyPair().get().getPrivate().toString());
            assertEquals(expected.getKeyPair().get().getPublic().toString(), actual.getKeyPair().get().getPublic().toString());
        } else {
            assertTrue(!expected.getKeyPair().isPresent() && !actual.getKeyPair().isPresent());
        }
        assertEquals(expected.getCertificateValidator().getClass(), actual.getCertificateValidator().getClass());
    }

}