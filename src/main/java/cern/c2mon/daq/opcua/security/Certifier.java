package cern.c2mon.daq.opcua.security;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;

/**
 * Interface for access of the configurations of the various endpoint security policies
 */
public interface Certifier {

    /**
     * Set the respective security configurations of an OpcUaClientConfigBuilder.
     * @param builder The builder object to modify according to the respective security policy
     * @return the modified builder
     */
    OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder);
}
