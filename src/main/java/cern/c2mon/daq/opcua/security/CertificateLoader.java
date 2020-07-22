package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import com.google.common.collect.ImmutableSet;
import io.netty.util.internal.StringUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * Loads a certificate and keypair from a keystore file configured in AppConfig, and configures a builder to use them.
 */
@Component("loader")
@Getter
@Slf4j
@RequiredArgsConstructor
public class CertificateLoader extends CertifierBase {
    private final AppConfigProperties.KeystoreConfig keystoreConfig;
    private final AppConfigProperties.PKIConfig pkiConfig;
    private static final ImmutableSet<Long> SEVERE_ERROR_CODES = ImmutableSet.<Long>builder().add(Bad_SecurityChecksFailed, Bad_CertificateUseNotAllowed, Bad_CertificateUriInvalid, Bad_CertificateUntrusted, Bad_CertificateTimeInvalid, Bad_CertificateRevoked, Bad_CertificateRevocationUnknown, Bad_CertificateIssuerRevocationUnknown, Bad_CertificateInvalid).build();

    /**
     * Loads a matching certificate and keypair if not yet present and returns whether they match the endpoint.
     * @param endpoint the endpoint for which certificate and keypair are generated.
     * @return whether certificate and keypair could be loaded and match the endpoint
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        if (certificate == null || keyPair == null) {
            keyPair = null;
            certificate = null;
            loadCertificateAndKeypair();
        }
        return existingCertificateMatchesEndpoint(endpoint);
    }

    /**
     * Checks whether the Certifier supports an endpoint's security policy and the corresponding signature algorithm.
     * The certificate is loaded if not yet present, as this is required to read it's signature algorithm.
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return canCertify(endpoint);
    }

    /**
     * If a connection using a Certifier fails for severe reasons, attempting to reconnect with this Certifier is
     * fruitless also with other endpoints.
     * @return a list of error codes which constitute a severe error.
     */
    @Override
    public Set<Long> getSevereErrorCodes() {
        return SEVERE_ERROR_CODES;
    }

    private void loadCertificateAndKeypair() {
        if (isKeystoreConfigured()) {
            log.info("Loading from pfx");
            try {
                final Map.Entry<X509Certificate, KeyPair> entry = PkiUtil.loadFromPfx(keystoreConfig);
                certificate = entry.getKey();
                keyPair = entry.getValue();
            } catch (ConfigurationException e) {
                log.error("An error occurred loading the certificate and keypair from pfx. ", e);
            }
        }
        if (keyPair == null || certificate == null && isPkiConfigured()) {
            log.info("Loading from PEM files");
            try {
                final PrivateKey privateKey = PkiUtil.loadPrivateKey(pkiConfig.getPkPath());
                certificate = PkiUtil.loadCertificate(pkiConfig.getCrtPath());
                keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
            } catch (ConfigurationException e) {
                log.error("An error occurred loading certificate and private key. ", e);
            }
        }
    }

    private boolean isKeystoreConfigured() {
        return keystoreConfig != null &&
                Stream.of(keystoreConfig.getType(), keystoreConfig.getPath(), keystoreConfig.getAlias()).noneMatch(StringUtil::isNullOrEmpty) &&
                Files.exists(Paths.get(keystoreConfig.getPath()));
    }

    private boolean isPkiConfigured() {
        boolean isConfigComplete = pkiConfig != null &&
                !StringUtil.isNullOrEmpty(pkiConfig.getCrtPath()) &&
                !StringUtil.isNullOrEmpty(pkiConfig.getPkPath());
        return isConfigComplete && Files.exists(Paths.get(pkiConfig.getCrtPath())) && Files.exists(Paths.get(pkiConfig.getPkPath()));
    }
}
