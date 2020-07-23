package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * For those endpoints with a supported security policy, the CertificateGenerator generates a new self-signed
 * certificate and keypair with a matching signature algorithm. Currently, only @RsaSha256
 * (http://www.w3.org/2001/04/xmldsig-more#rsa-sha256) is supported.
 */
@Component("generator")
@Getter
@Slf4j
@RequiredArgsConstructor
public class CertificateGenerator extends CertifierBase {
    private static final String RSA_SHA256 = "SHA256withRSA";
    private static final String[] SUPPORTED_SIG_ALGS = {RSA_SHA256};
    private static final Set<Long> SEVERE_ERROR_CODES = Sets.newHashSet(Bad_CertificateUseNotAllowed, Bad_CertificateUriInvalid, Bad_CertificateUntrusted, Bad_CertificateTimeInvalid, Bad_CertificateRevoked, Bad_CertificateRevocationUnknown, Bad_CertificateIssuerRevocationUnknown);
    private final AppConfigProperties config;

    /**
     * Checks whether an endpoint's security policy is supported by the CertificateGenerator. This method only checks
     * whether the algorithms match. Loading or generating the certificate and keypair may still result in errors.
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri());
        if (!sp.isPresent()) {
            return false;
        }
        final String sigAlg = sp.get().getAsymmetricSignatureAlgorithm().getTransformation();
        return Arrays.stream(SUPPORTED_SIG_ALGS).anyMatch(s -> s.equalsIgnoreCase(sigAlg));
    }

    /**
     * Generates a matching certificate and keypair if not yet present and returns whether the endpoint can therewith be
     * certified.
     * @param endpoint the endpoint for which certificate and keypair are generated
     * @return whether the endpoint can be certified
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri());
        if (!sp.isPresent() || !supportsAlgorithm(endpoint)) {
            return false;
        }
        generateCertificate(sp.get());
        return keyPair != null && certificate != null;
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

    private void generateCertificate(SecurityPolicy securityPolicy) {
        if (existingCertificateMatchesSecurityPolicy(securityPolicy)) {
            return;
        }
        keyPair = null;
        certificate = null;
        if (securityPolicy.getAsymmetricSignatureAlgorithm().getTransformation().equalsIgnoreCase(RSA_SHA256)) {
            log.info("Generating self-signed certificate and keypair.");
            try {
                keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
                SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                        .setCommonName(config.getAppName())
                        .setOrganization(config.getOrganization())
                        .setOrganizationalUnit(config.getOrganizationalUnit())
                        .setLocalityName(config.getLocalityName())
                        .setStateName(config.getStateName())
                        .setCountryCode(config.getCountryCode())
                        .setApplicationUri(config.getApplicationUri());
                certificate = builder.build();
            } catch (NoSuchAlgorithmException e) {
                log.error("Could not generate RSA keypair");
            } catch (Exception e) {
                log.error("Could not generate certificate");
            }
        }
    }
}
