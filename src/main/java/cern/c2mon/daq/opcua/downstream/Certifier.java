package cern.c2mon.daq.opcua.downstream;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import java.util.Collection;

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

    /**
     * The Message Security Mode describes the security level for exchanges messages
     * @return the Message Security Mode of the respective security configuration
     */
    MessageSecurityMode getMessageSecurityMode();

    /**
     * The Security Policy is the name for the security algorithm and key length
     * @return the Security Policy of the respective security configuration
     */
    Collection<SecurityPolicy> getSecurityPolicy();

    static String getApplicationUri() {
        return "urn:cern:c2mon:daq:opcua";
    }
}
