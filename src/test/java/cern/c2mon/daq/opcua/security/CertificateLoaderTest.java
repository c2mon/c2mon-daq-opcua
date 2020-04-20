package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.iotedge.SecurityIT;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CertificateLoaderTest {
    AppConfig.KeystoreConfig config;
    CertificateLoader loader;

    List<SecurityPolicy> policies;

    @BeforeEach
    public void setUp() {
        String path = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
        config = AppConfig.KeystoreConfig.builder()
                .type("PKCS12")
                .path(path)
                .pwd("password")
                .alias("1")
                .build();
        loader = new CertificateLoader(config);

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
    public void shouldLoadCertificateFromConfig() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, UnrecoverableKeyException {
        KeyStore ks = loadFromKeyStore();
        PrivateKey expectedPrivate = (PrivateKey) ks.getKey(config.getAlias(), config.getPwd().toCharArray());
        final X509Certificate expectedCert = (X509Certificate) ks.getCertificate(config.getAlias());
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();

        loader.certify(actual, e);

        final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                .setCertificate(expectedCert)
                .setKeyPair(new KeyPair(expectedCert.getPublicKey(), expectedPrivate))
                .setEndpoint(e);

        CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
    }

    @Test
    void certifyShouldNotDoAnythingIfNotSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            loader.certify(actual, e);
            if (!loader.canCertify(e)) {
                CertificateGeneratorTest.assertEqualConfigFields(new OpcUaClientConfigBuilder(), actual);
            }
        }
    }

    @Test
    void canCertifyWithBadConfigShouldReturnFalse() {
        loader.getConfig().setPwd("false");
        final EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        assertFalse(loader.canCertify(e));
    }

    @Test
    void canCertifyWithBadCertificateFileReturnFalse() {
        loader.getConfig().setPath("bad");
        final EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        assertFalse(loader.canCertify(e));
    }

    @Test
    void certifyWithMisconfiguredEndpointShouldNotCertify() {
        assertFalse(loader.canCertify(createEndpointWithSecurityPolicy("badUri")));
    }

    private KeyStore loadFromKeyStore() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(config.getType());
        InputStream in = Files.newInputStream(Paths.get(config.getPath()));
        keyStore.load(in, config.getPwd().toCharArray());
        return keyStore;
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

}