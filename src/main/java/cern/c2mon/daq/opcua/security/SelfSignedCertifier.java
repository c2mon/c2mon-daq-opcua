package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.exceptions.CertificateBuilderException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * Provides the security configurations for an endpoint with Security Policy Basic128Rsa15,
 * generates a new self-signed certificate on every call
 */
@Slf4j
public class SelfSignedCertifier implements Certifier {

    @Setter
    @Autowired
    AppConfig config;

    protected KeyPair keyPair;
    protected X509Certificate certificate;

    @Override
    public OpcUaClientConfigBuilder configureSecuritySettings(OpcUaClientConfigBuilder builder) {
        try {
            return builder.setCertificate(getClientCertificate())
                    .setKeyPair(getKeyPair());
        } catch (CertificateBuilderException e) {
            log.error("Continue without security");
        }
        return builder;
    }

    private X509Certificate getClientCertificate() throws CertificateBuilderException {
        if (certificate == null) {
            generateSelfSignedCertificate();
        }
        return certificate;
    }

    private KeyPair getKeyPair() throws CertificateBuilderException {
        if (keyPair == null) {
            generateSelfSignedCertificate();
        }
        return keyPair;
    }


    protected void generateSelfSignedCertificate() throws CertificateBuilderException {
        //Generate self-signed certificate
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateBuilderException(CertificateBuilderException.Cause.RSA_KEYPAIR, e);
        }

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName(config.getAppName())
                .setOrganization(config.getOrganization())
                .setOrganizationalUnit(config.getOrganizationalUnit())
                .setLocalityName(config.getLocalityName())
                .setStateName(config.getStateName())
                .setCountryCode(config.getCountryCode())
                .setApplicationUri(config.getApplicationUri());
        try {
            certificate = builder.build();
        } catch (Exception e) {
            throw new CertificateBuilderException(CertificateBuilderException.Cause.CERTIFICATE_BUILDER, e);
        }
    }
}
