package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class CertificateLoader extends Certifier {
    final AppConfig.KeystoreConfig config;

    protected void process() {
        if (!isKeystoreConfigured()) {
            return;
        }
        log.info("Loading certificate from keystore");
        try (InputStream in = Files.newInputStream(Paths.get(config.getPath()))) {
            final var keyStore = KeyStore.getInstance(config.getType());
            keyStore.load(in, config.getPwd().toCharArray());
            Key privateKey = keyStore.getKey(config.getAlias(), config.getPkPwd().toCharArray());
            if (privateKey instanceof PrivateKey) {
                certificate = (X509Certificate) keyStore.getCertificate(config.getAlias());
                PublicKey publicKey = certificate.getPublicKey();
                keyPair = new KeyPair(publicKey, (PrivateKey) privateKey);
                certificate.getSigAlgName();
            }
        } catch (KeyStoreException e) {
            log.error("Cannot load Keystore. Proceed without. ", e);
        } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            log.error("An error occurred loading the defined certificate. ", e);
        }
    }

    private boolean isKeystoreConfigured() {
        return config != null &&
                Stream.of(config.getType(), config.getPath(), config.getAlias(), config.getPwd(), config.getPkPwd())
                        .noneMatch(s -> s == null || s.isBlank())  &&
                Files.exists(Paths.get(config.getPath()));
    }

    public boolean supports(String sigAlg) {
        return  sigAlg.equalsIgnoreCase(certificate.getSigAlgName());
    }
}
