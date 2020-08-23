package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityAlgorithm;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CertificateGeneratorTest {
    AppConfigProperties config;
    CertificateGenerator generator;
    List<MessageSecurityMode> modes;
    List<SecurityPolicy> policies;

    @BeforeEach
    public void setUp() {
        config = TestUtils.createDefaultConfig();
        generator = new CertificateGenerator(config);

        modes = new ArrayList<>(Arrays.asList(MessageSecurityMode.SignAndEncrypt, MessageSecurityMode.Sign, MessageSecurityMode.None));
        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));
    }

    @Test
    public void supportsAlgorithmShouldReturnFalseIfEndpointDescriptionIsNull() {
        assertFalse(generator.supportsAlgorithm(null));
    }

    @Test
    public void supportsAlgorithmShouldReturnFalseIfEndpointDescriptionHasBadSecurityPolicyURI() {
        assertFalse(supportAlgorithmWithSecurityPolicyUri("badSecurityPolicy"));
    }

    @Test
    public void supportsAlgorithmShouldReturnTrueForSecurityPolicyAlgorithmsWithSHA256withRSA() {
        assertPolicySupportStatus(true);
    }

    @Test
    public void supportsAlgorithmShouldReturnFalseForSecurityPolicyAlgorithmsWithoutSHA256withRSA() {
        assertPolicySupportStatus(false);
    }

    @Test
    public void supportsAlgorithmShouldReturnTrueForRsaSha256() {
        assertTrue(supportAlgorithmWithSecurityPolicyUri(SecurityPolicy.Basic256Sha256.getUri()));
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
    void certifyShouldNotSetEndpoint() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            generator.certify(actual, e);
            if (!generator.canCertify(e)) {
                final String message = assertThrows(NullPointerException.class, actual::build).getMessage();
                assertTrue(message.contains("endpoint must be non-null"));
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
        CertificateGenerator badGenerator = new CertificateGenerator(AppConfigProperties.builder().build());
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

    @Test
    public void uncertifyShouldRemoveValues() {
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder b = new OpcUaClientConfigBuilder();
        generator.certify(b, e);
        generator.uncertify(b);
        assertThrows(NullPointerException.class, b::build);
    }

    @Test
    public void certifyTheSameEndpointTwiceShouldNotRegenerateCertificate() {
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder b = new OpcUaClientConfigBuilder();
        generator.certify(b, e);
        final X509Certificate expectedCert = generator.certificate;
        final KeyPair expectedKp = generator.keyPair;
        generator.certify(b, e);
        assertEquals(expectedCert, generator.certificate);
        assertEquals(expectedKp, generator.keyPair);
    }

    @Test
    public void onlyCertificateLoadedShouldReloadBoth() {
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder b = new OpcUaClientConfigBuilder();
        generator.certify(b, e);
        final X509Certificate firstCert = generator.certificate;
        generator.keyPair = null;
        generator.certify(b, e);
        assertNotEquals(firstCert, generator.certificate);
    }

    private boolean supportAlgorithmWithSecurityPolicyUri(String securityPolicyUri) {
        final EndpointDescription e = new EndpointDescription("endpointUrl", null, null, null, securityPolicyUri, null, "transportProfileUri", null);
        return generator.supportsAlgorithm(e);
    }

    private void assertPolicySupportStatus(boolean shouldBeSupported) {
        final SecurityAlgorithm supportedAlgorithm = SecurityAlgorithm.RsaSha256;
        final Map<Boolean, List<SecurityPolicy>> expected = Stream.of(SecurityPolicy.values())
                .collect(Collectors.partitioningBy(p -> p.getAsymmetricSignatureAlgorithm().equals(supportedAlgorithm)));
        final Map<Boolean, List<SecurityPolicy>> actual = Stream.of(SecurityPolicy.values())
                .collect(Collectors.partitioningBy(p -> supportAlgorithmWithSecurityPolicyUri(p.getUri())));
        assertTrue(expected.get(shouldBeSupported).containsAll(actual.get(shouldBeSupported)));
    }


    private EndpointDescription createEndpointWithSecurityPolicy(String securityPolicyUri) {
        return new EndpointDescription("test", null, null, null, securityPolicyUri, null, null, null);
    }

    public static void assertEqualConfigFields(OpcUaClientConfigBuilder expectedBuilder, OpcUaClientConfigBuilder actualBuilder) {
        final OpcUaClientConfig actual = actualBuilder.build();
        final OpcUaClientConfig expected = expectedBuilder.build();
        assertEquals(expected.getEndpoint(), actual.getEndpoint());
        assertEquals(expected.getCertificate(), actual.getCertificate());
        if (expected.getKeyPair().isPresent() && actual.getKeyPair().isPresent()) {
            assertEquals(expected.getKeyPair().get().getPrivate().toString(), actual.getKeyPair().get().getPrivate().toString());
            assertEquals(expected.getKeyPair().get().getPublic().toString(), actual.getKeyPair().get().getPublic().toString());
        } else {
            assertFalse(expected.getKeyPair().isPresent() || actual.getKeyPair().isPresent());
        }
        assertEquals(expected.getCertificateValidator().getClass(), actual.getCertificateValidator().getClass());
    }

}