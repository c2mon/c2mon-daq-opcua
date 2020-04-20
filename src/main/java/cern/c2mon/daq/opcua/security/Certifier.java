package cern.c2mon.daq.opcua.security;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

/**
 * A certifier adds a certificate and keypair matching an endpoint's security algorithm to the endpoint.
 */
public interface Certifier {
    /**
     * Append the endpoint as well as a certificate and keypair matching the endpoint's security policy to the builder,
     * given that the endpoint is supported.
     * @param builder the builder to configure to use the certificate and keypair
     * @param endpoint the certificate and keypair are chosen to match the endpoint's security policy. The endpoint is
     *                 also appended to the builder.
     */
    void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint);

    /**
     * Checks whether the certifier supports an endpoint's security policy and the corresponding signature algorithm.
     * This method only checks wether the algorithms match, but does not load or generate the certifiate and keypair.
     * Loading or generating the ceritificate and keypair may still result in errors, for example due to bad
     * configuration settings.
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    boolean supportsAlgorithm(EndpointDescription endpoint);

    /**
     * Loads or generates a matching certificate and keypair if not yet present and returns whether the endpoint can
     * thus be certified.
     * @param endpoint the endpoint for which certificate and keypair are generated or loaded.
     * @return whether the endpoint can be certified.
     */
    boolean canCertify(EndpointDescription endpoint);
}
