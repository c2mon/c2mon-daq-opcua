package cern.c2mon.daq.opcua.downstream;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public interface Certifier {

    OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder);
    X509Certificate getClientCertificate();
    KeyPair getKeyPair();
    MessageSecurityMode getMessageSecurityMode();
    SecurityPolicy getSecurityPolicy();
    IdentityProvider getIdentityProvider();
}
