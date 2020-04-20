package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import io.netty.util.internal.StringUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

@Component
@Getter
@Slf4j
@RequiredArgsConstructor
@Qualifier("loader")
public class CertificateLoader extends CertifierBase {
    final AppConfig.KeystoreConfig config;

    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        if (canCertify(endpoint)) {
            super.certify(builder, endpoint);
        }
    }

    public boolean canCertify(EndpointDescription endpoint) {
        if (certificate == null || keyPair == null) {
            keyPair = null;
            certificate = null;
            loadCertificate();
        }
        return existingCertificateMatchesEndpoint(endpoint);
    }


    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return canCertify(endpoint);
    }

    private void loadCertificate() {
        if (!isKeystoreConfigured()) {
            return;
        }
        log.info("Loading certificate from keystore");
        try (InputStream in = Files.newInputStream(Paths.get(config.getPath()))) {
            final KeyStore keyStore = KeyStore.getInstance(config.getType());
            keyStore.load(in, config.getPwd().toCharArray());
            Key privateKey = keyStore.getKey(config.getAlias(), config.getPwd().toCharArray());
            if (privateKey instanceof PrivateKey) {
                certificate = (X509Certificate) keyStore.getCertificate(config.getAlias());
                PublicKey publicKey = certificate.getPublicKey();
                keyPair = new KeyPair(publicKey, (PrivateKey) privateKey);
            }
        } catch (KeyStoreException e) {
            log.error("Cannot load Keystore. Proceed without. ", e);
        } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            log.error("An error occurred loading the defined certificate. ", e);
        }
    }

    private boolean isKeystoreConfigured() {
        return config != null &&
                Stream.of(config.getType(), config.getPath(), config.getAlias())
                        .noneMatch(s -> s == null || StringUtil.isNullOrEmpty(s))  &&
                Files.exists(Paths.get(config.getPath()));
    }
}
