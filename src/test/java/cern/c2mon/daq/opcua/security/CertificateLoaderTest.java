package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.iotedge.SecurityIT;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CertificateLoaderTest {
    AppConfigProperties.KeystoreConfig ksConfig;
    AppConfigProperties.PKIConfig pkiConfig;
    CertificateLoader loader;

    List<SecurityPolicy> policies;

    @BeforeEach
    public void setUp() {
        String path = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
        ksConfig = AppConfigProperties.KeystoreConfig.builder()
                .type("PKCS12")
                .path(path)
                .pwd("password")
                .alias("1")
                .build();
        pkiConfig = AppConfigProperties.PKIConfig.builder()
                .crtPath(SecurityIT.class.getClassLoader().getResource("server.crt").getPath())
                .pkPath(SecurityIT.class.getClassLoader().getResource("pkcs8server.key").getPath())
                .build();
        loader = new CertificateLoader(ksConfig, pkiConfig);

        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));

    }
    @Test
    void certifyShouldModifyBuilderIfSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            loader.certify(actual, e);
            if (loader.canCertify(e)) {
                final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                        .setCertificate(loader.certificate)
                        .setKeyPair(loader.keyPair)
                        .setEndpoint(e);
                CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
            }
        }
    }

    @Test
    public void shouldLoadFromPfxIfExists() throws ConfigurationException {
        final Map.Entry<X509Certificate, KeyPair> entry = PkiUtil.loadFromPfx(ksConfig);

        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();

        loader.certify(actual, e);

        final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                .setCertificate(entry.getKey())
                .setKeyPair(entry.getValue())
                .setEndpoint(e);

        CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
    }

    @Test
    public void shouldLoadFromPkiIfNoPfxConfigured() throws ConfigurationException {
        final X509Certificate certificate = PkiUtil.loadCertificate(pkiConfig.getCrtPath());
        final PrivateKey privateKey = PkiUtil.loadPrivateKey(pkiConfig.getPkPath());

        final CertificateLoader pkiLoader = new CertificateLoader(null, pkiConfig);
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
        pkiLoader.certify(actual, e);

        final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                .setCertificate(certificate)
                .setKeyPair(new KeyPair(certificate.getPublicKey(), privateKey))
                .setEndpoint(e);

        CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
    }

    @Test
    void certifyShouldNotSetEndpointIfNotSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            loader.certify(actual, e);
            if (!loader.canCertify(e)) {
                final var message = assertThrows(NullPointerException.class, actual::build).getMessage();
                assertTrue(message.contains("endpoint must be non-null"));
            }
        }
    }

    @Test
    void canCertifyWithBadFilesReturnsFalse() {
        loader.getKeystoreConfig().setPath("bad");
        loader.getPkiConfig().setCrtPath("bad");
        final EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        assertFalse(loader.canCertify(e));
    }

    @Test
    void certifyWithMisconfiguredEndpointShouldNotCertify() {
        assertFalse(loader.canCertify(createEndpointWithSecurityPolicy("badUri")));
    }

    private EndpointDescription createEndpointWithSecurityPolicy(String securityPolicyUri) {
        return new EndpointDescription("test", null, null, null, securityPolicyUri, null, null, null);
    }

}