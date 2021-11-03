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

import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NoValidCertificates;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_SecurityChecksFailed;

/**
 * A Certifier to configure an {@link OpcUaClientConfigBuilder} for a connection with an endpoint without encryption.
 */
@Slf4j
@RequiredArgsConstructor
@EquipmentScoped
public class NoSecurityCertifier implements Certifier {
    /**
     * Append configures the builder to connect to the endpoint without security.
     * @param builder  the builder to configure for connection
     * @param endpoint the endpoint to which the connection shall be configured.
     */
    @Override
    public void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint) {
        builder.setEndpoint(endpoint);
    }

    /**
     * Remove any configuration of the fields "Endpoint".
     * @param builder the builder from which to remove Certifier-specific fields
     */
    @Override
    public void uncertify(OpcUaClientConfigBuilder builder) {
        builder.setCertificate(null).setKeyPair(null).setEndpoint(null);
    }

    /**
     * An endpoint can be certified if it's {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy} and its
     * {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None. Behavior is equal to
     * canCertify.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
    @Override
    public boolean supportsAlgorithm(EndpointDescription endpoint) {
        return endpoint.getSecurityMode().equals(MessageSecurityMode.None)
                && endpoint.getSecurityPolicyUri().equalsIgnoreCase(SecurityPolicy.None.getUri());
    }

    /**
     * An endpoint can be certified if its {@link org.eclipse.milo.opcua.stack.core.security.SecurityPolicy} and its
     * {@link org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode} are None.
     * @param endpoint the endpoint to check.
     * @return whether it's possible to connect to the endpoint without security.
     */
    @Override
    public boolean canCertify(EndpointDescription endpoint) {
        return supportsAlgorithm(endpoint);
    }

    /**
     * If a connection without configures security fails for any security-related reasons, reattempts are fruitless.
     * @param code the error code to check for severity
     * @return whether the error code is constitutes a severe error.
     */
    @Override
    public boolean isSevereError(long code) {
        return code == Bad_SecurityChecksFailed || code == Bad_NoValidCertificates;
    }
}
