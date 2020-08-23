package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.iotedge.SecurityIT;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;
import static org.junit.jupiter.api.Assertions.*;

class CertificateLoaderTest {

    String pfxPath = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
    String keyPath = SecurityIT.class.getClassLoader().getResource("pkcs8server.key").getPath();
    String certPath = SecurityIT.class.getClassLoader().getResource("server.crt").getPath();
    AppConfigProperties.PKIConfig pkiConfig;
    AppConfigProperties.KeystoreConfig ksConfig;
    CertificateLoader loader;
    List<SecurityPolicy> policies;

    @BeforeEach
    public void setUp() {
        ksConfig = new AppConfigProperties.KeystoreConfig("PKCS12", pfxPath, "password", "1");
        pkiConfig = new AppConfigProperties.PKIConfig(keyPath, certPath);
        loader = new CertificateLoader(ksConfig, pkiConfig);
        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));

    }

    @Test
    public void certifyShouldModifyBuilderIfSupported() {
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
    public void supportsAlgorithmShouldReturnEqualValuesAsCanCertify() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            loader.certify(actual, e);
            if (loader.supportsAlgorithm(e)) {
                final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                        .setCertificate(loader.certificate)
                        .setKeyPair(loader.keyPair)
                        .setEndpoint(e);
                CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
            }
        }
    }

    @Test
    public void certifyShouldLoadFromPfxIfExists() throws ConfigurationException {
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
    public void certifyShouldLoadFromPkiIfPfxThrowsException() throws ConfigurationException {
        ksConfig.setPath(certPath);
        final Map.Entry<X509Certificate, KeyPair> certKp = PkiUtil.loadFromPki(pkiConfig);
        final CertificateLoader pkiLoader = new CertificateLoader(null, pkiConfig);
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
        pkiLoader.certify(actual, e);
        final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                .setCertificate(certKp.getKey())
                .setKeyPair(certKp.getValue())
                .setEndpoint(e);
        CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
    }

    @Test
    public void certifyWithInvalidKsAndPkShouldNotSetAnyValues() {
        ksConfig.setPath(certPath);
        pkiConfig.setCertificatePath(pfxPath);
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
        loader.certify(actual, e);
        assertThrows(NullPointerException.class, actual::build);
    }

    @Test
    public void certifyShouldLoadFromPkiIfNoPfxConfigured() throws ConfigurationException {
        final Map.Entry<X509Certificate, KeyPair> certKp = PkiUtil.loadFromPki(pkiConfig);
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
        loader.certify(actual, e);
        final OpcUaClientConfigBuilder expected = new OpcUaClientConfigBuilder()
                .setCertificate(certKp.getKey())
                .setKeyPair(certKp.getValue())
                .setEndpoint(e);
        CertificateGeneratorTest.assertEqualConfigFields(expected, actual);
    }

    @Test
    public void certifyShouldNotSetEndpointIfNotSupported() {
        for (SecurityPolicy p : policies) {
            EndpointDescription e = createEndpointWithSecurityPolicy(p.getUri());
            OpcUaClientConfigBuilder actual = new OpcUaClientConfigBuilder();
            loader.certify(actual, e);
            if (!loader.canCertify(e)) {
                final String message = assertThrows(NullPointerException.class, actual::build).getMessage();
                assertTrue(message.contains("endpoint must be non-null"));
            }
        }
    }

    @Test
    public void canCertifyWithBadFilesReturnsFalse() {
        final AppConfigProperties.KeystoreConfig badKsConfig = new AppConfigProperties.KeystoreConfig("PKCS12", "bad", "password", "1");
        final AppConfigProperties.PKIConfig badPkiConfig = new AppConfigProperties.PKIConfig("bad","bad");
        final CertificateLoader badLoader = new CertificateLoader(badKsConfig, badPkiConfig);
        final EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        assertFalse(badLoader.canCertify(e));
    }

    @Test
    public void certifyWithMisconfiguredEndpointShouldNotCertify() {
        assertFalse(loader.canCertify(createEndpointWithSecurityPolicy("badUri")));
    }

    @Test
    public void isSevereErrorShouldReturnTrueForSecurityChecksAndCertificateInvalid() {
        assertTrue(loader.isSevereError(Bad_SecurityChecksFailed));
        assertTrue(loader.isSevereError(Bad_CertificateInvalid));
    }

    @Test
    public void isSevereErrorShouldReturnTrueForBaseErrors() {
        assertTrue(loader.isSevereError(Bad_CertificateTimeInvalid));
    }

    @Test
    public void isSevereErrorShouldReturnFalseForOtherCodes() {
        assertFalse(loader.isSevereError(Bad_AggregateNotSupported));
    }

    @Test
    public void certifyTheSameEndpointTwiceShouldNotRegenerateCertificate() {
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder b = new OpcUaClientConfigBuilder();
        loader.certify(b, e);
        final X509Certificate expectedCert = loader.certificate;
        final KeyPair expectedKp = loader.keyPair;
        loader.certify(b, e);
        assertEquals(expectedCert, loader.certificate);
        assertEquals(expectedKp, loader.keyPair);
    }

    @Test
    public void onlyKeyPairLoadedShouldReloadBoth() {
        EndpointDescription e = createEndpointWithSecurityPolicy(SecurityPolicy.Basic256Sha256.getUri());
        OpcUaClientConfigBuilder b = new OpcUaClientConfigBuilder();
        loader.certify(b, e);
        final KeyPair firstKp = loader.keyPair;
        loader.certificate = null;
        loader.certify(b, e);
        assertNotEquals(firstKp, loader.keyPair);
    }

    private EndpointDescription createEndpointWithSecurityPolicy(String securityPolicyUri) {
        return new EndpointDescription("test", null, null, null, securityPolicyUri, null, null, null);
    }

}