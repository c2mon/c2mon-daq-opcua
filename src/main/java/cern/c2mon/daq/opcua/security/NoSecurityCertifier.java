package cern.c2mon.daq.opcua.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Getter
@Slf4j
@NoArgsConstructor
@Qualifier("nosecurity")
/**
 * A certifier to configure an {@link OpcUaClientConfigBuilder} for a connection with an endpoint without encryption.
 */
public class NoSecurityCertifier extends CertifierBase {
    /**
     * Append configures the builder to connect to the endpoint without security.
     * @param builder the builder to configure for connection
     * @param endpoint the endpoint to which the connection shall be configured.
     */
    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        builder.setEndpoint(endpoint);
    }

    /**
     * An endpoint can be certified if it's {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy}
     * and its {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
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
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return endpoint.getSecurityLevel().intValue() == 0;
    }
}
