package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.CertificateBuilderException;
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
import java.util.Arrays;
import java.util.Collection;

/**
 * Provides the security configurations for an endpoint with Security Policy Basic128Rsa15,
 * generates a new self-signed certificate on every call
 */
@Slf4j
public class SelfSignedCertifier implements Certifier {

    protected KeyPair keyPair;
    protected X509Certificate certificate;

    @Override
    public OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder){
        return builder.setCertificate(getClientCertificate())
                .setKeyPair(getKeyPair())
                .setIdentityProvider(getIdentityProvider());
    }

    private X509Certificate getClientCertificate() {
        if (certificate == null) {
            generateSelfSignedCertificate();
        }
        return certificate;
    }

    private KeyPair getKeyPair() {
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
    public Collection<SecurityPolicy> getSecurityPolicy() {
        return Arrays.asList(SecurityPolicy.Basic128Rsa15, SecurityPolicy.Basic256Sha256);
    }

    private IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

    protected void generateSelfSignedCertificate() {
        //Generate self-signed certificate
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateBuilderException(CertificateBuilderException.Cause.RSA_KEYPAIR, e);
        }

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("C2MON DAQ OPCUA")
                .setOrganization("CERN")
                .setOrganizationalUnit("C2MON team")
                .setLocalityName("Geneva")
                .setStateName("Geneva")
                .setCountryCode("CH")
                .setApplicationUri(Certifier.getApplicationUri());

        try {
            certificate = builder.build();
        } catch (Exception e) {
            throw new CertificateBuilderException(CertificateBuilderException.Cause.CERTIFICATE_BUILDER, e);
        }
    }
}
