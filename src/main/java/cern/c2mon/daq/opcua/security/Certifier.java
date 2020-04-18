package cern.c2mon.daq.opcua.security;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public abstract class Certifier {
    KeyPair keyPair;
    X509Certificate certificate;

    public boolean canCertify() {
        if (keyPair != null && certificate != null) {
            return true;
        }
        process();
        return keyPair != null && certificate != null;
    }

    public void certify(OpcUaClientConfigBuilder builder) {
        if (canCertify()) {
            builder.setCertificate(certificate).setKeyPair(keyPair);
        }
    }

    protected abstract void process();

    public abstract boolean supports(String sigAlg);
}
