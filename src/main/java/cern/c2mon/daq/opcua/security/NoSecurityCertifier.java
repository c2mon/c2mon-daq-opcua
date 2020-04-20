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
public class NoSecurityCertifier extends CertifierBase {

    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        builder.setEndpoint(endpoint);
    }

    public boolean canCertify(EndpointDescription endpoint) {
        return supportsAlgorithm(endpoint);
    }

    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return endpoint.getSecurityLevel().intValue() == 0;
    }
}
