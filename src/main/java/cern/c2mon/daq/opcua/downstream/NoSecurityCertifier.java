package cern.c2mon.daq.opcua.downstream;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import java.util.Collection;
import java.util.Collections;

/**
 * Provides the security configurations to communicate with an endpoint insecurely without authentication.
 */
@Slf4j
public class NoSecurityCertifier implements  Certifier{

    @Override
    public OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder) {
        return builder;
    }

    public MessageSecurityMode getMessageSecurityMode() {
        return MessageSecurityMode.None;
    }

    public Collection<SecurityPolicy> getSecurityPolicy() {
        return Collections.singletonList(SecurityPolicy.None);
    }
}
