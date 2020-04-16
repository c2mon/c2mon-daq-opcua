package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.configuration.AuthConfig;
import cern.c2mon.daq.opcua.exceptions.SecurityProviderException;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.junit.jupiter.api.Assertions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityProviderTest {

    List<MessageSecurityMode> modes;
    List<SecurityPolicy> policies;

    SecurityProvider p = new SecurityProvider();
    AppConfig config;
    AuthConfig auth;


    @BeforeEach
    public void setUp() {
        auth = AuthConfig.builder().build();
        config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .auth(auth)
                .build();

        p.setConfig(config);

        modes = new ArrayList<>(Arrays.asList(MessageSecurityMode.SignAndEncrypt, MessageSecurityMode.Sign, MessageSecurityMode.None));
        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));

    }

    @Test
    public void certifyBuilderWithSecurityShouldAppendKeyPairAndCertificate() throws SecurityProviderException {
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
    public void certifyBuilderWithoutSecurityPolicyShouldNotChangeBuilder() throws SecurityProviderException {
        OpcUaClientConfig expected = OpcUaClientConfig.builder().build();
        for (MessageSecurityMode m : modes) {
            OpcUaClientConfig actual = certifyWith(SecurityPolicy.None, m);
            compareConfig(expected, actual);
        }
    }

    @Test
    public void certifyBuilderWithModeNoneShouldNotChangeBuilder() throws SecurityProviderException {
        OpcUaClientConfig expected = OpcUaClientConfig.builder().build();
        for (SecurityPolicy policy : policies) {
            OpcUaClientConfig actual = certifyWith(policy, MessageSecurityMode.None);
            compareConfig(expected, actual);
        }
    }

    @Test
    public void builderShouldUseCertificateFromKeystoreIfExists() throws SecurityProviderException, KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        setupAuthForCertificate();
        KeyStore ks = loadFromKeyStore();
        Key expectedPrivate = ks.getKey(auth.getKeyStoreAlias(), auth.getPrivateKeyPassword().toCharArray());
        X509Certificate expectedCert = (X509Certificate) ks.getCertificate(auth.getKeyStoreAlias());
        PublicKey expectedPublic = expectedCert.getPublicKey();

        OpcUaClientConfig config = certifyWith(SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt);

        Assertions.assertEquals(expectedCert.toString(), config.getCertificate().get().toString());
        Assertions.assertEquals(expectedPrivate.toString(), config.getKeyPair().get().getPrivate().toString());
        Assertions.assertEquals(expectedPublic.toString(), config.getKeyPair().get().getPublic().toString());
    }

    private void setupAuthForCertificate(){
        String path = SecurityProviderTest.class.getClassLoader().getResource("keystore.pfx").getPath();
        auth.setKeyStoreType("PKCS12");
        auth.setKeyStorePath(path);
        auth.setKeyStorePassword("password");
        auth.setPrivateKeyPassword("password");
        auth.setKeyStoreAlias("1");
    }

    private KeyStore loadFromKeyStore() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(auth.getKeyStoreType());
        InputStream in = Files.newInputStream(Paths.get(auth.getKeyStorePath()));
        keyStore.load(in, auth.getKeyStorePassword().toCharArray());
        return keyStore;
    }

    private void compareConfig(OpcUaClientConfig a, OpcUaClientConfig b) {
        assertEquals(a.getApplicationName(), b.getApplicationName());
        assertEquals(a.getCertificate(), b.getCertificate());
        assertEquals(a.getCertificateChain(), b.getCertificateChain());
        assertEquals(a.getCertificateValidator().getClass(), b.getCertificateValidator().getClass());
        assertEquals(a.getKeyPair(), b.getKeyPair());
    }

    private OpcUaClientConfig certifyWith(SecurityPolicy sp, MessageSecurityMode mode) throws SecurityProviderException {
        OpcUaClientConfigBuilder builder = OpcUaClientConfig.builder();
        p.certifyBuilder(builder, sp, mode);
        return builder.build();
    }


}