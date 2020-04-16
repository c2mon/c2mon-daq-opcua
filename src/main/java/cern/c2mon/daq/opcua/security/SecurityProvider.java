package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.configuration.AuthConfig;
import cern.c2mon.daq.opcua.exceptions.SecurityProviderException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
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
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Service
@Setter
@Slf4j
public class SecurityProvider {

    @Autowired
    AppConfig config;
    AuthConfig auth;
    Path serverKeyStorePath;
    KeyPair keyPair;
    X509Certificate certificate;
    KeyStore keyStore;

    public void certifyBuilder(OpcUaClientConfigBuilder builder, SecurityPolicy securityPolicy, MessageSecurityMode securityMode) throws SecurityProviderException {
        if (securityPolicy == SecurityPolicy.None || securityMode == MessageSecurityMode.None) {
            log.info("Not using security. Config Security Policy {}, MessageSecurityMode {}. ", securityPolicy, securityMode);
            return;
        }
        synchronized (this) {
            loadKeyStore();
            if (keyStore != null) {
                loadCertificate(this.keyStore);
            }
            if (keyStore != null && (certificate == null || keyPair == null)) {
                generateSelfSigned();
                store();
            }
            if (certificate == null || keyPair == null) {
                generateSelfSigned();
            }
            builder.setCertificate(certificate).setKeyPair(keyPair);
        }
    }

    private void loadKeyStore() {
        if (!isKeyStoreConfigured()) {
            log.info("Not all configuration required for the keystore is given. Proceed without.");
        } else {
            log.info("Loading keystore...");
            try {
                keyStore = KeyStore.getInstance(auth.getKeyStoreType());
            } catch (KeyStoreException e) {
                log.error("Cannot load Keystore. Proceed without. ", e);
            }
        }
    }

    private boolean isKeyStoreConfigured() {
        auth = config.getAuth();
        return auth != null && auth.getKeyStoreType() != null && auth.getKeyStorePath() != null &&
                auth.getPrivateKeyPassword() != null && auth.getKeyStorePassword() != null &&
                auth.getKeyStoreAlias() != null;
    }

    private void loadCertificate(KeyStore keyStore) {
        if (!Files.exists(getKeyStorePath())) {
            log.info("The configured keystore file does not exist. Proceeding without loading the certificate.");
        } else {
            log.info("Loading certificate...");
            try {
                InputStream in = Files.newInputStream(getKeyStorePath());
                keyStore.load(in, auth.getKeyStorePassword().toCharArray());
                Key privateKey = keyStore.getKey(auth.getKeyStoreAlias(), auth.getPrivateKeyPassword().toCharArray());
                if (privateKey instanceof PrivateKey) {
                    certificate = (X509Certificate) keyStore.getCertificate(auth.getKeyStoreAlias());
                    PublicKey publicKey = certificate.getPublicKey();
                    keyPair = new KeyPair(publicKey, (PrivateKey) privateKey);
                }
            } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
                log.error("An error occurred loading the defined certificate. ", e);
            }
        }
    }

    protected void generateSelfSigned() throws SecurityProviderException {
        log.info("Generating self-signed certificate and keypair.");
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityProviderException(SecurityProviderException.Cause.RSA_KEYPAIR, e);
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
            throw new SecurityProviderException(SecurityProviderException.Cause.CERTIFICATE_BUILDER, e);
        }
    }

    private void store() {
        log.info("Storing certificate in keystore");
        try {
            keyStore.setKeyEntry(auth.getKeyStoreAlias(), keyPair.getPrivate(), auth.getKeyStorePassword().toCharArray(), new X509Certificate[]{certificate});
            OutputStream out = Files.newOutputStream(getKeyStorePath());
            keyStore.store(out, auth.getKeyStorePassword().toCharArray());
        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            log.error("An error ocurred storing self signed certificates in keystore. ", e);
        }
    }

    private Path getKeyStorePath() {
        if (serverKeyStorePath == null) {
            serverKeyStorePath = Paths.get(auth.getKeyStorePath());
        }
        return serverKeyStorePath;
    }
}
