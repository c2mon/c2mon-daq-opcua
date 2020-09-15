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
package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.UriModifier;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.Certifier;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.client.security.ClientCertificateValidator;
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Chooses an endpoint to connect to and creates a client as configured in {@link AppConfigProperties}, selecting from a
 * list of endpoints.
 */
@Slf4j
@RequiredArgsConstructor
@EquipmentScoped
public class SecurityModule {
    private final AppConfigProperties config;
    private final UriModifier modifier;
    private final CertificateLoader loader;
    private final CertificateGenerator generator;
    private final NoSecurityCertifier noSecurity;
    private final List<AppConfigProperties.CertifierMode> certifiers = new ArrayList<>();
    private OpcUaClientConfigBuilder builder;

    /**
     * Creates an {@link OpcUaClient} according to the configuration specified in {@link AppConfigProperties} and
     * connect to it. The endpoint to connect to is chosen according to the security policy. Preference is given to
     * endpointDescriptions to which a connection can be made using a certificate loaded from a keystore file, is so configured. If
     * this is not possible and on-the-fly certificate generation is enabled, self-signed certificates will be generated
     * to attempt connection with appropriate endpointDescriptions. Connection with an insecure endpoint are attempted only if this
     * is not possible either and the option is allowed in the configuration.
     * @param discoveryUri the URI used for discovering the server
     * @param endpointDescriptions A list of endpointDescriptions of which to connect to one.
     * @return The {@link OpcUaClient} object that is connected to one of the
     * @throws OPCUAException if a failure occurred when establishing a secure channel. That exception may be type
     * {@link ConfigurationException} if the connection failed due to a configuration issue, or of type {@link
     * CommunicationException} if the connection attempt failed for reasons unrelated to configuration where a retry
     * may be fruitful.
     */
    public OpcUaClient createClient(String discoveryUri, Collection<EndpointDescription> endpointDescriptions) throws OPCUAException {
        builder = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english(config.getApplicationName()))
                .setApplicationUri(config.getApplicationUri())
                .setRequestTimeout(uint(config.getRequestTimeout()))
                .setCertificateValidator(getValidator());

        // assure that endpointDescriptions are in a mutable list
        List<EndpointDescription> endpoints = endpointDescriptions.stream()
                .map(e -> new EndpointDescription(
                        modifier.updateEndpointUrl(discoveryUri, e.getEndpointUrl()),
                        e.getServer(),
                        e.getServerCertificate(),
                        e.getSecurityMode(),
                        e.getSecurityPolicyUri(),
                        e.getUserIdentityTokens(),
                        e.getTransportProfileUri(),
                        e.getSecurityLevel()))
                .sorted(Comparator.comparing(EndpointDescription::getSecurityLevel).reversed())
                .collect(Collectors.toList());
        sortCertifiers();
        return attemptConnection(endpoints);
    }

    private ClientCertificateValidator getValidator() throws ConfigurationException {
        if (config.isTrustAllServers()) {
            return new ClientCertificateValidator.InsecureValidator();
        }
        if (!StringUtil.isNullOrEmpty(config.getPkiBaseDir())) {
            try {
                final File tempDir = new File(config.getPkiBaseDir());
                final DefaultTrustListManager trustListManager = new DefaultTrustListManager(tempDir);
                return new DefaultClientCertificateValidator(trustListManager);
            } catch (IOException e) {
                throw new ConfigurationException(ExceptionContext.PKI_ERROR, e);
            }
        }
        throw new ConfigurationException(ExceptionContext.PKI_ERROR);
    }

    private void sortCertifiers() {
        if (certifiers.isEmpty() && config.getCertifierPriority() != null) {
            certifiers.addAll(config.getCertifierPriority().entrySet().stream()
                    .filter(e -> e.getValue() != 0)
                    .sorted((o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
            log.info("Sorted certifiers into order: {}", certifiers.stream().map(Enum::name).collect(Collectors.joining(", ")));
        }
        if (certifiers.isEmpty()) {
            certifiers.add(AppConfigProperties.CertifierMode.NO_SECURITY);
        }
    }

    private OpcUaClient attemptConnection(List<EndpointDescription> mutableEndpoints) throws OPCUAException {
        OpcUaClient client = null;
        for (int i = 0; i < certifiers.size(); i++) {
            final Certifier certifier = getCertifierForMode(certifiers.get(i));
                log.info("Attempt connection with Certifier '{}'! ", certifier.getClass().getName());
                try {
                    client = attemptConnectionWithCertifier(mutableEndpoints, certifier);
                    break;
                } catch (OPCUAException e) {
                    log.info("Unable to connect with Certifier {}. Last encountered exception: ", certifier.getClass().getName(), e);
                    if (i >= certifiers.size() - 1) {
                        throw e;
                    }
                }
        }
        return client;
    }

    private Certifier getCertifierForMode(AppConfigProperties.CertifierMode mode) {
        switch (mode) {
            case LOAD:
                return loader;
            case GENERATE:
                return generator;
            default:
                return noSecurity;
        }
    }

    private OpcUaClient attemptConnectionWithCertifier(List<EndpointDescription> endpoints, Certifier certifier) throws OPCUAException {
        Exception lastException = null;
        final List<EndpointDescription> matchingEndpoints = endpoints.stream().filter(certifier::supportsAlgorithm).collect(Collectors.toList());
        for (EndpointDescription e : matchingEndpoints) {
            if (certifier.canCertify(e)) {
                log.info("Attempt authentication with mode {} and algorithm {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
                certifier.certify(builder, e);
                try {
                    OpcUaClient client = OpcUaClient.create(builder.build());
                    return (OpcUaClient) client.connect().join();
                } catch (UaException ex) {
                    lastException = ex;
                    log.debug("Unsupported transport in endpoint URI. Attempting less secure endpoint", ex);
                } catch (CompletionException ex) {
                    lastException = ex;
                    if (!handleAndShouldContinue(certifier, ex)) {
                        break;
                    }
                } finally {
                    certifier.uncertify(builder);
                }
            }
        }
        if (lastException == null) {
            throw new CommunicationException(ExceptionContext.AUTH_ERROR);
        } else {
            throw OPCUAException.of(ExceptionContext.AUTH_ERROR, lastException, false);
        }
    }

    private boolean handleAndShouldContinue(Certifier certifier, CompletionException ex) {
        final Throwable cause = ex.getCause();
        log.debug("Authentication error: ", cause);
        if (cause instanceof UnknownHostException) {
            log.debug("The host is unknown. Check if the server returns local URIs and configure replacement if required.");
        } else if (!(cause instanceof UaException)) {
            log.debug("Unexpected error in connection.");
        } else {
            final long code = ((UaException) cause).getStatusCode().getValue();
            if (OPCUAException.isSecurityIssue((UaException) cause)) {
                log.error("Your app security settings for Certifier {} seem to be invalid.", certifier.getClass().getName());
            } else if (certifier.isSevereError(code)) {
                log.error("Cannot connect to this server with Certifier {}.", certifier.getClass().getName());
            } else {
                log.debug("Attempting less secure endpoint: ", ex);
                return true;
            }
        }
        return false;
    }
}
