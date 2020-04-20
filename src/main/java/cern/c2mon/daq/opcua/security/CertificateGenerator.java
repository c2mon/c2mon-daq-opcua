package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;

@Component
@Slf4j
@Getter
@RequiredArgsConstructor
@Qualifier("generator")
/**
 * For those endpoints with a supported security policy, the CertificateGenerator generates a new self-signed certificate
 * and keypair with a matching signature algorithm.
 * Currently, only @RsaSha256 (http://www.w3.org/2001/04/xmldsig-more#rsa-sha256) is supported.
 */
public class CertificateGenerator extends CertifierBase {
    final AppConfig config;

    private static final String RSA_SHA256 = "SHA256withRSA";
    private static final String[] SUPPORTED_SIG_ALGS = {RSA_SHA256};

    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        if (canCertify(endpoint)) {
            super.certify(builder, endpoint);
        }
    }

    public boolean canCertify(EndpointDescription endpoint) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri());
        if (!sp.isPresent()  || !supportsAlgorithm(endpoint)) {
            return false;
        }
        generateCertificate(sp.get());
        return keyPair != null && certificate != null;
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

    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri());
        if (!sp.isPresent()) {
            return false;
        }
        final String sigAlg = sp.get().getAsymmetricSignatureAlgorithm().getTransformation();
        return Arrays.stream(SUPPORTED_SIG_ALGS).anyMatch(s -> s.equalsIgnoreCase(sigAlg));
    }
}
