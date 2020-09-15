/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityAlgorithm;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;

/**
 * For those endpoints with a supported security policy, the CertificateGenerator generates a new self-signed
 * certificate and keypair with a matching signature algorithm. Currently, only @RsaSha256
 * (http://www.w3.org/2001/04/xmldsig-more#rsa-sha256) is supported.
 */
@Slf4j
@RequiredArgsConstructor
@EquipmentScoped
public class CertificateGenerator extends CertifierBase {
    private static final String[] SUPPORTED_SIG_ALGS = {SecurityAlgorithm.RsaSha256.getTransformation()};
    private final AppConfigProperties config;


    /**
     * Checks whether an endpoint's security policy is supported by the CertificateGenerator. This method only checks
     * whether the algorithms match. Loading or generating the certificate and keypair may still result in errors.
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        if (endpoint != null) {
            final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri());
            if (sp.isPresent()) {
                final String sigAlg = sp.get().getAsymmetricSignatureAlgorithm().getTransformation();
                return Arrays.stream(SUPPORTED_SIG_ALGS).anyMatch(s -> s.equalsIgnoreCase(sigAlg));
            }
        }
        return false;
    }

    /**
     * Generates a matching certificate and keypair if not yet present and returns whether the endpoint can therewith be
     * certified.
     * @param endpoint the endpoint for which certificate and keypair are generated
     * @return whether the endpoint can be certified
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        final Optional<SecurityPolicy> sp = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri());
        return (sp.isPresent() && supportsAlgorithm(endpoint)) && generateCertificateIfMissing(sp.get());
    }

    private boolean generateCertificateIfMissing(SecurityPolicy securityPolicy) {
        return (existingCertificateMatchesSecurityPolicy(securityPolicy)) ||
                (securityPolicy.getAsymmetricSignatureAlgorithm().equals(SecurityAlgorithm.RsaSha256)
                        && generateRSASHA256());
    }

    private boolean generateRSASHA256() {
        log.info("Generating self-signed certificate and keypair.");
        try {
            final KeyPair tmpKp = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(tmpKp)
                    .setCommonName(config.getApplicationName())
                    .setOrganization(config.getOrganization())
                    .setOrganizationalUnit(config.getOrganizationalUnit())
                    .setLocalityName(config.getLocalityName())
                    .setStateName(config.getStateName())
                    .setCountryCode(config.getCountryCode())
                    .setApplicationUri(config.getApplicationUri());
            X509Certificate tmpCert = builder.build();
            if (tmpCert != null) {
                keyPair = tmpKp;
                certificate = tmpCert;
                return true;
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("Could not generate RSA keypair.", e);
        } catch (Exception e) {
            log.error("Could not generate certificate.", e);
        }
        return false;
    }
}
