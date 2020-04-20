package cern.c2mon.daq.opcua.security;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * The abstract base class for a @{@link Certifier}.
 */
public abstract class CertifierBase implements Certifier {
    KeyPair keyPair;
    X509Certificate certificate;

    /**
     * Append the endpoint as well as a certificate and keypair matching the endpoint's security policy to the builder,
     * given that the endpoint is supported.
     * @param builder the builder to configure to use the certificate and keypair
     * @param endpoint the certificate and keypair are chosen to match the endpoint's security policy. The endpoint is
     *                 also appended to the builder.
     */
    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        if (canCertify(endpoint)) {
            builder.setCertificate(certificate).setKeyPair(keyPair).setEndpoint(endpoint);
        }
    }

    protected boolean existingCertificateMatchesEndpoint(EndpointDescription e) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(e.getSecurityPolicyUri());
        return sp.filter(this::existingCertificateMatchesSecurityPolicy).isPresent();
    }

    protected boolean existingCertificateMatchesSecurityPolicy(SecurityPolicy p) {
        return keyPair != null && certificate != null && p.getAsymmetricSignatureAlgorithm().getTransformation().equalsIgnoreCase(certificate.getSigAlgName());
    }
}
