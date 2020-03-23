package cern.c2mon.daq.opcua.downstream;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

@Slf4j
public class NoSecurityCertifier implements  Certifier{

    @Override
    public OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder) {
        return builder;
    }

    public X509Certificate getClientCertificate() {
        return null;
    }
    public KeyPair getKeyPair() {
        return null;
    }

    public MessageSecurityMode getMessageSecurityMode() {
        return MessageSecurityMode.None;
    }

    public SecurityPolicy getSecurityPolicy() {
        return SecurityPolicy.None;
    }

    public IdentityProvider getIdentityProvider() {
        return null;
    }
}
