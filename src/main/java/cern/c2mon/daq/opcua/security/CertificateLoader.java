/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Map;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_CertificateInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_SecurityChecksFailed;

/**
 * Loads a certificate and keypair from a keystore file configured in AppConfig, and configures a builder to use them.
 */
@Slf4j
@RequiredArgsConstructor
@EquipmentScoped
public class CertificateLoader extends CertifierBase {
    private final AppConfigProperties.KeystoreConfig keystoreConfig;
    private final AppConfigProperties.PKIConfig pkiConfig;

    /**
     * Checks whether the Certifier supports an endpoint's security policy and the corresponding signature algorithm.
     * The certificate is loaded if not yet present, as this is required to read it's signature algorithm.
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return canCertify(endpoint);
    }

    /**
     * Loads a matching certificate and keypair if not yet present and returns whether they match the endpoint.
     * @param endpoint the endpoint for which certificate and keypair are generated.
     * @return whether certificate and keypair could be loaded and match the endpoint
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        if (certificate == null || keyPair == null) {
            loadCertificateAndKeypair();
        }
        return SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri())
                .filter(this::existingCertificateMatchesSecurityPolicy)
                .isPresent();
    }

    /**
     * If a connection using a Certifier fails for severe reasons, attempting to reconnect with this Certifier is
     * fruitless also with other endpoints.
     * @param code the error code to check for severity
     * @return whether the error code is constitutes a severe error.
     */
    @Override
    public boolean isSevereError(long code) {
        return code == Bad_SecurityChecksFailed || code == Bad_CertificateInvalid || super.isSevereError(code);
    }

    private void loadCertificateAndKeypair() {
        Map.Entry<X509Certificate, KeyPair> entry = null;
        if (PkiUtil.isKeystoreConfigured(keystoreConfig)) {
            log.info("Loading from pfx");
            try {
                entry = PkiUtil.loadFromPfx(keystoreConfig);
            } catch (ConfigurationException e) {
                log.error("An error occurred loading the certificate and keypair from pfx. ", e);
            }
        }
        if (entry == null && PkiUtil.isPkiConfigured(pkiConfig)) {
            log.info("Loading from PEM files");
            try {
                entry = PkiUtil.loadFromPki(pkiConfig);
            } catch (ConfigurationException e) {
                log.error("An error occurred loading certificate and private key. ", e);
            }
        }
        if (entry != null) {
            certificate = entry.getKey();
            keyPair = entry.getValue();
        }

    }
}
