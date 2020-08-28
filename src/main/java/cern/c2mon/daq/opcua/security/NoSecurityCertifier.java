package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NoValidCertificates;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_SecurityChecksFailed;

/**
 * A Certifier to configure an {@link OpcUaClientConfigBuilder} for a connection with an endpoint without encryption.
 */
@Slf4j
@RequiredArgsConstructor
@EquipmentScoped
public class NoSecurityCertifier implements Certifier {
    /**
     * Append configures the builder to connect to the endpoint without security.
     * @param builder  the builder to configure for connection
     * @param endpoint the endpoint to which the connection shall be configured.
     */
    @Override
    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        builder.setEndpoint(endpoint);
    }

    /**
     * Remove any configuration of the fields "Endpoint".
     * @param builder the builder from which to remove Certifier-specific fields
     */
    @Override
    public void uncertify(OpcUaClientConfigBuilder builder) {
        builder.setCertificate(null).setKeyPair(null).setEndpoint(null);
    }

    /**
     * An endpoint can be certified if it's {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy} and its
     * {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None. Behavior is equal to
     * canCertify.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return endpoint.getSecurityMode().equals(MessageSecurityMode.None)
                && endpoint.getSecurityPolicyUri().equalsIgnoreCase(SecurityPolicy.None.getUri());
    }

    /**
     * An endpoint can be certified if its {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy} and its
     * {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        return supportsAlgorithm(endpoint);
    }

    /**
     * If a connection without configures security fails for any security-related reasons, reattempts are fruitless.
     * @param code the error code to check for severity
     * @return whether the error code is constitutes a severe error.
     */
    @Override
    public boolean isSevereError(long code) {
        return code == Bad_SecurityChecksFailed || code == Bad_NoValidCertificates;
    }
}
