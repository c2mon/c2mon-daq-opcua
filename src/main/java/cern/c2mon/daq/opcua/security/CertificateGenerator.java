package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityAlgorithm;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;

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
    private static final String[] SUPPORTED_SIG_ALGS = {SecurityAlgorithm.RsaSha256.getTransformation()};
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
        return (sp.isPresent() && supportsAlgorithm(endpoint)) && generateCertificateIfMissing(sp.get());
    }

    private boolean generateCertificateIfMissing(SecurityPolicy securityPolicy) {
        return (existingCertificateMatchesSecurityPolicy(securityPolicy)) ||
                (securityPolicy.getAsymmetricSignatureAlgorithm().equals(SecurityAlgorithm.RsaSha256) && generateRSASHA256());
    }

    private boolean generateRSASHA256() {
        try {
            log.info("Generating self-signed certificate and keypair.");
            final KeyPair tmpKp = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(tmpKp)
                    .setCommonName(config.getApplicationName())
                    .setOrganization(config.getOrganization())
                    .setOrganizationalUnit(config.getOrganizationalUnit())
                    .setLocalityName(config.getLocalityName())
                    .setStateName(config.getStateName())
                    .setCountryCode(config.getCountryCode())
                    .setApplicationUri(config.getApplicationUri());
            X509Certificate tmpCert = builder.build();
            if (tmpCert != null) {
                keyPair = tmpKp;
                certificate = tmpCert;
                return true;
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("Could not generate RSA keypair.", e);
        } catch (Exception e) {
            log.error("Could not generate certificate.", e);
        }
        return false;
    }
}
