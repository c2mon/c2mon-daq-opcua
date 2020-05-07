package cern.c2mon.daq.opcua.security;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * The abstract base class for a @{@link Certifier} using a certificate and keypair.
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
    @Override
    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        if (canCertify(endpoint)) {
            builder.setCertificate(certificate).setKeyPair(keyPair).setEndpoint(endpoint);
        }
    }

    /**
     * Remove any configuration of the fields "Certificate", "KeyPair" and "Endpoint".
     * @param builder the builder from which to remove Certifier-specific fields
     */
    @Override
    public void uncertify(OpcUaClientConfigBuilder builder) {
        builder.setCertificate(null).setKeyPair(null).setEndpoint(null);
    }

    protected boolean existingCertificateMatchesEndpoint(EndpointDescription e) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(e.getSecurityPolicyUri());
        return sp.filter(this::existingCertificateMatchesSecurityPolicy).isPresent();
    }

    protected boolean existingCertificateMatchesSecurityPolicy(SecurityPolicy p) {
        return keyPair != null && certificate != null && p.getAsymmetricSignatureAlgorithm().getTransformation().equalsIgnoreCase(certificate.getSigAlgName());
    }
}
