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
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

/**
 * A certifier adds a certificate and keypair matching an endpoint's security algorithm to the endpoint.
 */
public interface Certifier {
    /**
     * Append the endpoint as well as a certificate and keypair matching the endpoint's security policy to the builder,
     * given that the endpoint is supported.
     * @param builder  the builder to configure for connection
     * @param endpoint configure the builder according to the endpoint's security policy settings.
     */
    void certify(OpcUaClientConfigBuilder builder, EndpointDescription endpoint);

    /**
     * Remove any configuration of those fields of the builder that the Certifier sets in "certify".
     * @param builder the builder from which to remove Certifier-specific fields
     */
    void uncertify(OpcUaClientConfigBuilder builder);

    /**
     * Checks whether the Certifier supports an endpoint's security policy and the corresponding signature algorithm.
     * This method only checks whether the algorithms match, but does not load or generate the certificate and keypair.
     * Loading or generating the certificate and keypair may still result in errors, for example due to bad
     * configuration settings.
     * @param endpoint the endpoint whose security policy to check to be supported.
     * @return whether the endpoint's security policy is supported.
     */
    boolean supportsAlgorithm(EndpointDescription endpoint);

    /**
     * Loads or generates the certificate and keypair for an endpoint if it's security policy is supported and not yet
     * done, and returns whether the process was successful, they match the endpoint and the endpoint can be certified.
     * @param endpoint the endpoint for which certificate and keypair are generated or loaded.
     * @return whether certificate and keypair could be loaded or generated and match the endpoint.
     */
    boolean canCertify(EndpointDescription endpoint);

    /**
     * If a connection using a Certifier fails for severe reasons, attempting to reconnect with this Certifier is
     * fruitless also with other endpoints.
     * @param code the error code to check for severity
     * @return whether the error code is constitutes a severe error.
     */
    boolean isSevereError(long code);
}
