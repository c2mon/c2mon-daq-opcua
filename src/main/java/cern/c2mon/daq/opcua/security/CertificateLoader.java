package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.AppConfig;
import com.google.common.collect.ImmutableSet;
import io.netty.util.internal.StringUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.stream.Stream;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_CertificateInvalid;

/**
 * Loads a certificate and keypair from a keystore file configured in AppConfig, and configures a builder to use them.
 */
@Component("loader")
@Getter
@Slf4j
@RequiredArgsConstructor
public class CertificateLoader extends CertifierBase {
    final AppConfig.KeystoreConfig config;
    private static final ImmutableSet<Long> SEVERE_ERROR_CODES = ImmutableSet.<Long>builder().add(Bad_SecurityChecksFailed, Bad_CertificateUseNotAllowed, Bad_CertificateUriInvalid, Bad_CertificateUntrusted, Bad_CertificateTimeInvalid, Bad_CertificateRevoked, Bad_CertificateRevocationUnknown, Bad_CertificateIssuerRevocationUnknown, Bad_CertificateInvalid).build();

    /**
     * Loads a matching certificate and keypair if not yet present and returns whether they match the endpoint.
     *
     * @param endpoint the endpoint for which certificate and keypair are generated.
     * @return whether certificate and keypair could be loaded and match the endpoint
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        if (certificate == null || keyPair == null) {
            keyPair = null;
            certificate = null;
            loadCertificate();
        }
        return existingCertificateMatchesEndpoint(endpoint);
    }

    /**
     * Checks whether the certifier supports an endpoint's security policy and the corresponding signature algorithm.
     * The certifiate is loaded if not yet present, as this is required to read it's signature algorithm.
     *
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return canCertify(endpoint);
    }

    /**
     * If a connection using a Certifier fails for severe reasons, attemping to reconnect with this certifier is
     * fruitless also with other endpoints.
     *
     * @return a list of error codes which constitute a severe error.
     */
    @Override
    public Set<Long> getSevereErrorCodes() {
        return SEVERE_ERROR_CODES;
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
                Stream.of(config.getType(), config.getPath(), config.getAlias()).noneMatch(s -> s == null || StringUtil.isNullOrEmpty(s)) &&
                Files.exists(Paths.get(config.getPath()));
    }
}
