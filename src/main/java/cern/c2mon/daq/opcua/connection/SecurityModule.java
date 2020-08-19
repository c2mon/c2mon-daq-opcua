package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.UriModifier;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.security.Certifier;
import com.google.common.collect.ImmutableMap;
import io.netty.util.internal.StringUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Chooses an endpoint to connect to and creates a client as configured in {@link AppConfigProperties}, selecting from a
 * list of endpoints.
 */
@Component("securityModule")
@Slf4j
public class SecurityModule {
    private final AppConfigProperties config;
    private final UriModifier modifier;
    private final Map<String, Certifier> key2Certifier;
    private OpcUaClientConfigBuilder builder;

    /**
     * Creates a new SecurityModule instance and assigns the {@link Certifier} a priority depending on the
     * configuration
     * @param config     the up-to-date application properties containing information that shall be passed in initial
     *                   connection to the server
     * @param modifier the UriModifier to process the URLs contained in the {@link EndpointDescription} as configured.
     * @param loader     the {@link Certifier} responsible for loading a certificate and keypair from local storage
     * @param generator  the {@link Certifier} responsible for creating a new self-signed certificate and keypair
     * @param noSecurity the {@link Certifier} responsible for establishing connection without security
     */
    @Autowired
    public SecurityModule(AppConfigProperties config, UriModifier modifier, Certifier loader, Certifier generator, Certifier noSecurity) {
        this.config = config;
        this.modifier = modifier;
        key2Certifier = ImmutableMap.<String, Certifier>builder()
                .put("none", noSecurity)
                .put("generate", generator)
                .put("load", loader)
                .build();
    }

    /**
     * Creates an {@link OpcUaClient} according to the configuration specified in {@link AppConfigProperties} and
     * connect to it. The endpoint to connect to is chosen according to the security policy. Preference is given to
     * endpointDescriptions to which a connection can be made using a certificate loaded from a keystore file, is so configured. If
     * this is not possible and on-the-fly certificate generation is enabled, self-signed certificates will be generated
     * to attempt connection with appropriate endpointDescriptions. Connection with an insecure endpoint are attempted only if this
     * is not possible either and the option is allowed in the configuration.
     * @param endpointDescriptions A list of endpointDescriptions of which to connect to one.
     * @return An {@link CompletableFuture} containing the {@link OpcUaClient} object that is connected to one of the
     * endpointDescriptions, or an exception that caused a failure in establishing a secure channel. That exception may be type
     * {@link ConfigurationException} if the connection failed due to a configuration issue, and of type {@link
     * CommunicationException} if the connection attempt failed for reasons unrelated to configuration, where a retry
     * may be fruitful.
     */
    public OpcUaClient createClient(String discoveryUri, Collection<EndpointDescription> endpointDescriptions) throws OPCUAException {
        builder = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english(config.getApplicationName()))
                .setApplicationUri(config.getApplicationUri())
                .setRequestTimeout(uint(config.getRequestTimeout()))
                .setCertificateValidator(getValidator());

        List<String> certifiers = config.getCertifierPriority().entrySet().stream()
                .sorted((o1, o2) -> -o1.getValue().compareTo(o2.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

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
        try {
            final OpcUaClient client = attemptConnection(certifiers, endpoints);
            log.info("Connected successfully!");
            return client;
        } catch (Exception e) {
            throw OPCUAException.of(ExceptionContext.AUTH_ERROR, e, false);
        }
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

    private OpcUaClient attemptConnection(List<String> sortedCertifiers, List<EndpointDescription> mutableEndpoints) throws Exception {
        OpcUaClient client = null;
        final Iterator<String> i = sortedCertifiers.iterator();
        while (i.hasNext()) {
            final Certifier certifier = key2Certifier.get(i.next());
            try {
                log.info("Attempt connection with Certifier '{}'! ", certifier.getClass().getName());
                client = attemptConnectionWithCertifier(mutableEndpoints, certifier);
                break;
            } catch (Exception e) {
                log.info("Unable to connect with Certifier {}. Last encountered exception: ", certifier.getClass().getName(), e);
                if (!i.hasNext()) {
                    throw e;
                }
            }
        }
        return client;
    }

    private OpcUaClient attemptConnectionWithCertifier(List<EndpointDescription> endpoints, Certifier certifier) throws Exception {
        Exception lastException = new CommunicationException(ExceptionContext.AUTH_ERROR);
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
        throw lastException;
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
