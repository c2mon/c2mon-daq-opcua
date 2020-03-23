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

@Slf4j
public class SelfSignedCertifier implements Certifier {

    private IdentityProvider identityProvider;
    protected KeyPair keyPair;
    protected X509Certificate certificate;

    private String certificatePath = "opcua.cert";

    @Override
    public OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder) {
        return builder.setCertificate(getClientCertificate())
                .setKeyPair(getKeyPair())
                .setIdentityProvider(getIdentityProvider());
    }

    @Override
    public X509Certificate getClientCertificate() {
        if (certificate == null) {
            generateSelfSignedCertificate();
        }
        return certificate;
    }

    @Override
    public KeyPair getKeyPair() {
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
        return SecurityPolicy.Basic256Sha256;
    }

    @Override
    public IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

    protected void generateSelfSignedCertificate() {
        //Generate self-signed certificate
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException n) {
            log.error("Could not generate RSA Key Pair.", n);
            System.exit(1);
        }

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("Eclipse Milo Example Client")
                .setOrganization("digitalpetri")
                .setOrganizationalUnit("dev")
                .setLocalityName("Folsom")
                .setStateName("CA")
                .setCountryCode("US")
                .setApplicationUri("urn:eclipse:milo:examples:client");

        try {
            certificate = builder.build();
        } catch (Exception e) {
            log.error("Could not build certificate.", e);
            System.exit(1);
        }
    }
}
