package cern.c2mon.daq.opcua.downstream;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * Provides the security configurations for an endpoint with Security Policy Basic128Rsa15,
 * generates a new self-signed certificate on every call
 */
//TODO wrap in custom exception
@Slf4j
public class SelfSignedCertifier implements Certifier {

    protected KeyPair keyPair;
    protected X509Certificate certificate;

    @Override
    public OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder) throws Exception {
        return builder.setCertificate(getClientCertificate())
                .setKeyPair(getKeyPair())
                .setIdentityProvider(getIdentityProvider());
    }

    private X509Certificate getClientCertificate() throws Exception {
        if (certificate == null) {
            generateSelfSignedCertificate();
        }
        return certificate;
    }

    private KeyPair getKeyPair() throws Exception {
        if (keyPair == null) {
            generateSelfSignedCertificate();
        }
        return keyPair;
    }

    @Override
    public MessageSecurityMode getMessageSecurityMode() {
        return MessageSecurityMode.SignAndEncrypt;
    }

    @Override
    public SecurityPolicy getSecurityPolicy() {
        return SecurityPolicy.Basic128Rsa15;
    }

    private IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

    protected void generateSelfSignedCertificate() throws Exception {
        //Generate self-signed certificate
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException n) {
            log.error("Could not generate RSA Key Pair.", n);
            throw n;
        }

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("C2MON DAQ OPCUA")
                .setOrganization("CERN")
                .setOrganizationalUnit("C2MON team")
                .setLocalityName("Geneva")
                .setStateName("Geneva")
                .setCountryCode("CH")
                .setApplicationUri("urn:cern:c2mon:daq:opcua");

        try {
            certificate = builder.build();
        } catch (Exception e) {
            log.error("Could not build certificate.", e);
            throw e;
        }
    }
}
