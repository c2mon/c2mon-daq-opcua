package cern.c2mon.daq.opcua.security;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.util.Set;

/**
 * A certifier adds a certificate and keypair matching an endpoint's security algorithm to the endpoint.
 */
public interface Certifier {
    /**
     * Append the endpoint as well as a certificate and keypair matching the endpoint's security policy to the builder,
     * given that the endpoint is supported.
     *
     * @param builder  the builder to configure for connection
     * @param endpoint configure the builder according to the endpoint's security policy settings.
     */
    void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint);

    /**
     * Remove any configuration of those fields of the builder that the Certifier sets in "certify".
     *
     * @param builder the builder from which to remove Certifier-specific fields
     */
    void uncertify(OpcUaClientConfigBuilder builder);

    /**
     * Checks whether the certifier supports an endpoint's security policy and the corresponding signature algorithm.
     * This method only checks whether the algorithms match, but does not load or generate the certifiate and keypair.
     * Loading or generating the ceritificate and keypair may still result in errors, for example due to bad
     * configuration settings.
     *
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    boolean supportsAlgorithm(EndpointDescription endpoint);

    /**
     * Loads or generates the certificate and keypair for an endpoint if it's security policy is supported and not yet
     * done, and returns whether the process was successful, they match the endpoint and the endpoint can be certified.
     *
     * @param endpoint the endpoint for which certificate and keypair are generated or loaded.
     * @return whether certificate and keypair could be loaded or generated and match the endpoint.
     */
    boolean canCertify(EndpointDescription endpoint);

    /**
     * If a connection using a Certifier fails for severe reasons, attemping to reconnect with this certifier is
     * fruitless also with other endpoints.
     *
     * @return a list of error codes which constitute a severe error.
     */
    Set<Long> getSevereErrorCodes();
}
