package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.configuration.AuthConfig;
import cern.c2mon.daq.opcua.exceptions.CertificateBuilderException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

@Service
@Setter
@Slf4j
public class SecurityProvider {
    /**
     * The Message Security Mode describes the security level for exchanges messages
     * @return the Message Security Mode of the respective security configuration
     */
    private static final List<MessageSecurityMode> securityModes = Arrays.asList(
            MessageSecurityMode.SignAndEncrypt, MessageSecurityMode.Sign, MessageSecurityMode.None);

    /**
     * The Security Policy is the name for the security algorithm and key length
     * @return the Security Policy of the respective security configuration
     */
    private static final List<SecurityPolicy> securityPolicies = Arrays.asList(SecurityPolicy.Basic256Sha256, SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256);

    @Autowired
    AppConfig config;

    AuthConfig auth;
    Path serverKeyStorePath;
    KeyPair keyPair;
    X509Certificate certificate;
    KeyStore keyStore;

    public void certifyBuilder(OpcUaClientConfigBuilder builder, SecurityPolicy securityPolicy, MessageSecurityMode securityMode) throws CertificateBuilderException {
        auth = config.getAuth();
        if (securityPolicy == SecurityPolicy.None || securityMode == MessageSecurityMode.None) {
            log.info("Not using security. Config Security Policy {}, MessageSecurityMode {}. ", securityPolicy, securityMode);
            return;
        }

        KeyStore keyStore = loadKeyStore();
        if (keyStore != null && isCertificatePresent()) {
            try {
                loadCertificate(this.keyStore);
            } catch (IOException | CertificateException | NoSuchAlgorithmException e) {
                try {
                    generateSelfSignedCertificateAndPutInKeyStore();
                } catch (CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException ex) {
                    log.error("Could not store self Signed Certificates in Keystore.", e);
                }
            }
        }
        generateSelfSignedCertificate();
        configureSecuritySettings(builder);
    }

    private void generateSelfSignedCertificateAndPutInKeyStore() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, CertificateBuilderException {
        keyStore.load(null, auth.getKeyStorePassword().toCharArray());
        generateSelfSignedCertificate();
        keyStore.setKeyEntry(auth.getKeyStoreAlias(), keyPair.getPrivate(),
                auth.getKeyStorePassword().toCharArray(), new X509Certificate[]{certificate});
        OutputStream out = Files.newOutputStream(getKeyStorePath());
        keyStore.store(out, auth.getKeyStorePassword().toCharArray());
    }

    private KeyStore loadKeyStore() {
        if (keyStore == null && auth != null && auth.getKeyStoreType() != null) {
            try {
                keyStore = KeyStore.getInstance(auth.getKeyStoreType());
            } catch (KeyStoreException e) {
                log.error("Cannot load Keystore. Proceed without. ", e);
            }
        }
        return keyStore;
    }

    private void configureSecuritySettings(OpcUaClientConfigBuilder builder){
        builder.setCertificate(certificate)
                .setKeyPair(keyPair);
    }

    private boolean isCertificatePresent() {
        return auth != null && auth.getKeyStorePath() != null && Files.exists(getKeyStorePath());
    }

    private Path getKeyStorePath() {
        if (serverKeyStorePath == null) {
            serverKeyStorePath = Paths.get(auth.getKeyStorePath());
        }
        return serverKeyStorePath;
    }

    private void loadCertificate(KeyStore keyStore) throws IOException, CertificateException, NoSuchAlgorithmException {
        InputStream in = Files.newInputStream(Paths.get(auth.getKeyStorePath()));
        keyStore.load(in, auth.getKeyStorePassword().toCharArray());
    }

    private IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

    protected void generateSelfSignedCertificate() throws CertificateBuilderException {
        if (keyPair != null && certificate != null) {
            return;
        }
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateBuilderException(CertificateBuilderException.Cause.RSA_KEYPAIR, e);
        }

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName(config.getAppName())
                .setOrganization(config.getOrganization())
                .setOrganizationalUnit(config.getOrganizationalUnit())
                .setLocalityName(config.getLocalityName())
                .setStateName(config.getStateName())
                .setCountryCode(config.getCountryCode())
                .setApplicationUri(config.getApplicationUri());
        try {
            certificate = builder.build();
        } catch (Exception e) {
            throw new CertificateBuilderException(CertificateBuilderException.Cause.CERTIFICATE_BUILDER, e);
        }
    }


}
