package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.exceptions.SecurityProviderException;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
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
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SecurityModuleTest {

    List<MessageSecurityMode> modes;
    List<SecurityPolicy> policies;

    SecurityModule p;
    AppConfig config;
    AppConfig.KeystoreConfig auth;


    @BeforeEach
    public void setUp() {
        auth = AppConfig.KeystoreConfig.builder().build();
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
                .keystore(auth)
                .build();
        p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config));

        modes = new ArrayList<>(Arrays.asList(MessageSecurityMode.SignAndEncrypt, MessageSecurityMode.Sign, MessageSecurityMode.None));
        policies = new ArrayList<>(Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256, SecurityPolicy.None));

    }

    @Test
    public void setSecurityShouldAppendKeyPairAndCertificate() throws SecurityProviderException {
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
    public void noSecurityPolicyShouldNotChangeBuilder() throws SecurityProviderException {
        OpcUaClientConfig expected = OpcUaClientConfig.builder().build();
        for (MessageSecurityMode m : modes) {
            OpcUaClientConfig actual = certifyWith(SecurityPolicy.None, m);
            compareConfig(expected, actual);
        }
    }

    @Test
    public void modeNoneShouldNotChangeBuilder() throws SecurityProviderException {
        OpcUaClientConfig expected = OpcUaClientConfig.builder().build();
        for (SecurityPolicy policy : policies) {
            OpcUaClientConfig actual = certifyWith(policy, MessageSecurityMode.None);
            compareConfig(expected, actual);
        }
    }

    @Test
    public void shouldUseCertificateFromKeystoreIfExists() throws SecurityProviderException, KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        setupAuthForCertificate();
        KeyStore ks = loadFromKeyStore();
        Key expectedPrivate = ks.getKey(auth.getAlias(), auth.getPkPwd().toCharArray());
        X509Certificate expectedCert = (X509Certificate) ks.getCertificate(auth.getAlias());
        PublicKey expectedPublic = expectedCert.getPublicKey();

        OpcUaClientConfig config = certifyWith(SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt);

        assertEquals(expectedCert.toString(), config.getCertificate().get().toString());
        assertEquals(expectedPrivate.toString(), config.getKeyPair().get().getPrivate().toString());
        assertEquals(expectedPublic.toString(), config.getKeyPair().get().getPublic().toString());
    }

    @Test
    public void authenticateWithUsrAndPwdShouldSetIdentityProvider() throws SecurityProviderException {
        setupAuthForUsrPwd();
        IdentityProvider expected = new UsernameProvider("user", "pwd");

        IdentityProvider actual = certifyWith(SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt)
                .getIdentityProvider();

        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void setUsrAndPwdForInsecureConnectionAsWell() throws SecurityProviderException {
        setupAuthForUsrPwd();
        OpcUaClientConfig actual = certifyWith(SecurityPolicy.None, MessageSecurityMode.None);
        assertTrue(actual.getIdentityProvider() instanceof UsernameProvider);
    }

    @Test
    public void setCertificateAndUsernameIfPossible() throws SecurityProviderException {
        setupAuthForCertificate();
        setupAuthForUsrPwd();
        OpcUaClientConfig actual = certifyWith(SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt);
        assertTrue(actual.getCertificate().isPresent());
        assertTrue(actual.getKeyPair().isPresent());
        assertTrue(actual.getIdentityProvider() instanceof UsernameProvider);
    }

    private void setupAuthForUsrPwd() {
        AppConfig.UsrPwdConfig usrPwdConfig = AppConfig.UsrPwdConfig.builder().usr("user").pwd("pwd").build();
        config.setUsrPwd(usrPwdConfig);
    }

    private void setupAuthForCertificate(){
        String path = SecurityModuleTest.class.getClassLoader().getResource("keystore.pfx").getPath();
        auth.setType("PKCS12");
        auth.setPath(path);
        auth.setPwd("password");
        auth.setPkPwd("password");
        auth.setAlias("1");
    }

    private KeyStore loadFromKeyStore() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(auth.getType());
        InputStream in = Files.newInputStream(Paths.get(auth.getPath()));
        keyStore.load(in, auth.getPwd().toCharArray());
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
        //p.certifyBuilder(builder, sp, mode);
        return builder.build();
    }

    @Test
    public void supportedAlgorithms() {
        TreeSet<String> algorithms = Arrays.stream(Security.getProviders())
                .flatMap(provider -> provider.getServices().stream())
                .filter(service -> service.getType().equals("Signature"))
                .map(Provider.Service::getAlgorithm)
                .collect(Collectors.toCollection(TreeSet::new));
        for (String algorithm : algorithms)
            System.out.println(algorithm);
    }

}