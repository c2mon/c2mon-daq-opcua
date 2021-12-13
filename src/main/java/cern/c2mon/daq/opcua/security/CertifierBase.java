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

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.stream.LongStream;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * The abstract base class for a @{@link Certifier} using a certificate and keypair.
 */
public abstract class CertifierBase implements Certifier {
    static final long[] SEVERE_ERROR_CODES = new long[]{
            Bad_CertificateUseNotAllowed,
            Bad_CertificateUriInvalid,
            Bad_CertificateUntrusted,
            Bad_CertificateTimeInvalid,
            Bad_CertificateRevoked,
            Bad_CertificateRevocationUnknown,
            Bad_CertificateIssuerRevocationUnknown};

    protected KeyPair keyPair;
    protected X509Certificate certificate;

    /**
     * Append the endpoint as well as a certificate and keypair matching the endpoint's security policy to the builder,
     * given that the endpoint is supported.
     * @param builder  the builder to configure to use the certificate and keypair
     * @param endpoint the certificate and keypair are chosen to match the endpoint's security policy. The endpoint is
     *                 also appended to the builder.
     */
    @Override
    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        if (canCertify(endpoint)) {
            builder.setCertificate(certificate).setKeyPair(keyPair).setEndpoint(endpoint);
        }
    }

    /**
     * Remove any configuration of the fields "Certificate", "KeyPair" and "Endpoint".
     * @param builder the builder from which to remove Certifier-specific fields
     */
    @Override
    public void uncertify(OpcUaClientConfigBuilder builder) {
        builder.setCertificate(null).setKeyPair(null).setEndpoint(null);
    }

    /**
     * Returns true for configuration errors, as as the configuration does not change, and for errors of trust.
     * @param code the error code to check for severity
     * @return whether the error code is constitutes a severe error.
     */
    @Override
    public boolean isSevereError(long code) {
        return LongStream.of(SEVERE_ERROR_CODES).anyMatch(l -> l == code);
    }

    protected boolean existingCertificateMatchesSecurityPolicy(SecurityPolicy p) {
        return keyPair != null && certificate != null && p.getAsymmetricSignatureAlgorithm().getTransformation().equalsIgnoreCase(certificate.getSigAlgName());
    }
}
