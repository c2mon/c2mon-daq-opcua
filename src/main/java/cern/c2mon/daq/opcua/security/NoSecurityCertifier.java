package cern.c2mon.daq.opcua.security;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * A Certifier to configure an {@link OpcUaClientConfigBuilder} for a connection with an endpoint without encryption.
 */
@Component("noSecurity")
@Getter
@Slf4j
@RequiredArgsConstructor
public class NoSecurityCertifier implements Certifier {
    /**
     * Append configures the builder to connect to the endpoint without security.
     * @param builder the builder to configure for connection
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
     * An endpoint can be certified if it's {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy}
     * and its {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        return supportsAlgorithm(endpoint);
    }

    /**
     * An endpoint can be certified if it's {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy}
     * and its {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None.
     * Behavior is equal to canCertify.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return endpoint.getSecurityMode().equals(MessageSecurityMode.None)
                && endpoint.getSecurityPolicyUri().equalsIgnoreCase(SecurityPolicy.None.getUri());
    }
    private static final ImmutableSet<Long> SEVERE_ERROR_CODES = ImmutableSet.<Long>builder().add(Bad_SecurityChecksFailed, Bad_NoValidCertificates).build();

    /**
     * If a connection using a Certifier fails for severe reasons, attempting to reconnect with this Certifier is
     * fruitless also with other endpoints.
     *
     * @return a list of error codes which constitute a severe error.
     */
    @Override
    public Set<Long> getSevereErrorCodes() {
        return SEVERE_ERROR_CODES;
    }
}
