package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.UriModifier;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.security.Certifier;
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
import org.springframework.stereotype.Component;

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
@Component("securityModule")
@Slf4j
@RequiredArgsConstructor
public class SecurityModule {
    private final AppConfigProperties config;
    private final UriModifier modifier;
    private final Certifier loader;
    private final Certifier generator;
    private final Certifier noSecurity;
    private OpcUaClientConfigBuilder builder;
    private final List<AppConfigProperties.CertifierMode> certifiers = new ArrayList<>();

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
        if (config.getCertifierPriority() != null) {
            certifiers.addAll(config.getCertifierPriority().entrySet().stream()
                    .filter(e -> e.getValue() != 0)
                    .sorted((o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
        } else {
            certifiers.add(AppConfigProperties.CertifierMode.NO_SECURITY);
        }
    }

    private OpcUaClient attemptConnection(List<EndpointDescription> mutableEndpoints) throws OPCUAException {
        OpcUaClient client = null;
        final Iterator<AppConfigProperties.CertifierMode> certifierIterator = certifiers.iterator();
        while (certifierIterator.hasNext()) {
            final Certifier certifier = getCertifierForMode(certifierIterator.next());
            log.info("Attempt connection with Certifier '{}'! ", certifier.getClass().getName());
            try {
                client = attemptConnectionWithCertifier(mutableEndpoints, certifier);
                break;
            } catch (OPCUAException e) {
                log.info("Unable to connect with Certifier {}. Last encountered exception: ", certifier.getClass().getName(), e);
                if (!certifierIterator.hasNext()) {
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
