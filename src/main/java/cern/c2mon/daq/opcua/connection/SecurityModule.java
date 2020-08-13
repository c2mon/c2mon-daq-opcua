package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
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
    private final Map<String, Certifier> key2Certifier;
    private OpcUaClientConfigBuilder builder;

    /**
     * Creates a new SecurityModule instance and assigns the {@link Certifier} a priority depending on the
     * configuration
     * @param config     the up-to-date application properties containing information that shall be passed in initial
     *                   connection to the server
     * @param loader     the {@link Certifier} responsible for loading a certificate and keypair from local storage
     * @param generator  the {@link Certifier} responsible for creating a new self-signed certificate and keypair
     * @param noSecurity the {@link Certifier} responsible for establishing connection without security
     */
    @Autowired
    public SecurityModule(AppConfigProperties config, Certifier loader, Certifier generator, Certifier noSecurity) {
        this.config = config;
        key2Certifier = ImmutableMap.<String, Certifier>builder()
                .put("none", noSecurity)
                .put("generate", generator)
                .put("load", loader)
                .build();
    }

    /**
     * Creates an {@link OpcUaClient} according to the configuration specified in {@link AppConfigProperties} and
     * connect to it. The endpoint to connect to is chosen according to the security policy. Preference is given to
     * endpoints to which a connection can be made using a certificate loaded from a keystore file, is so configured. If
     * this is not possible and on-the-fly certificate generation is enabled, self-signed certificates will be generated
     * to attempt connection with appropriate endpoints. Connection with an insecure endpoint are attempted only if this
     * is not possible either and the option is allowed in the configuration.
     * @param endpoints A list of endpoints of which to connect to one.
     * @return An {@link CompletableFuture} containing the {@link OpcUaClient} object that is connected to one of the
     * endpoints, or an exception that caused a failure in establishing a secure channel. That exception may be type
     * {@link ConfigurationException} if the connection failed due to a configuration issue, and of type {@link
     * CommunicationException} if the connection attempt failed for reasons unrelated to configuration, where a retry
     * may be fruitful.
     */
    public CompletableFuture<OpcUaClient> createClient(Collection<EndpointDescription> endpoints) {
        final CompletableFuture<OpcUaClient> clientFuture = new CompletableFuture<>();
        try {
            builder = OpcUaClientConfig.builder()
                    .setApplicationName(LocalizedText.english(config.getApplicationName()))
                    .setApplicationUri(config.getApplicationUri())
                    .setRequestTimeout(uint(config.getRequestTimeout()))
                    .setCertificateValidator(getValidator());

            // assure that endpoints is a mutable list
            List<EndpointDescription> mutableEndpoints = new ArrayList<>(endpoints);

            // prefer more secure endpoints
            mutableEndpoints.sort(Comparator.comparing(EndpointDescription::getSecurityLevel).reversed());

            List<Map.Entry<String, Integer>> toSort = new ArrayList<>(config.getCertifierPriority().entrySet());
            toSort.sort(Map.Entry.comparingByValue());
            for (Map.Entry<String, Integer> e : toSort) {
                final Certifier certifier = key2Certifier.get(e.getKey());
                OpcUaClient client = connectIfPossible(mutableEndpoints, certifier);
                if (client != null) {
                    log.info("Connected successfully with Certifier '{}'! ", e.getKey());
                    clientFuture.complete(client);
                }
            }
        } catch (ConfigurationException e) {
            clientFuture.completeExceptionally(e);
        }
        if (!clientFuture.isDone()) {
            clientFuture.completeExceptionally(new CommunicationException(ExceptionContext.AUTH_ERROR));
        }
        return clientFuture;
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

    private OpcUaClient connectIfPossible(List<EndpointDescription> endpoints, Certifier certifier) throws ConfigurationException {
        final List<EndpointDescription> matchingEndpoints = endpoints.stream().filter(certifier::supportsAlgorithm).collect(Collectors.toList());
        for (EndpointDescription e : matchingEndpoints) {
            if (certifier.canCertify(e)) {
                certifier.certify(builder, e);
                log.info("Attempt authentication with mode {} and algorithm {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
                try {
                    OpcUaClient client = OpcUaClient.create(builder.build());
                    return (OpcUaClient) client.connect().join();
                } catch (UaException ex) {
                    log.error("Unsupported transport in endpoint URI. Attempting less secure endpoint", ex);
                    endpoints.remove(e);
                    certifier.uncertify(builder);
                } catch (CompletionException ex) {
                    if (!handleAndReportWhetherToContinue(certifier, ex)) {
                        break;
                    }
                    certifier.uncertify(builder);
                }
            } else {
                log.info("Unable to certify endpoint {}, {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
            }
        }
        return null;
    }

    private boolean handleAndReportWhetherToContinue(Certifier certifier, CompletionException ex) throws ConfigurationException {
        final Throwable cause = ex.getCause();
        log.error("Authentication error: ", cause);
        if (!(cause instanceof UaException)) {
            log.error("Unexpected error in connection, abort connecting through Certifier {}.", certifier.getClass().getName());
            return false;
        }
        final long code = ((UaException) cause).getStatusCode().getValue();
        if (OPCUAException.isSecurityIssue((UaException) cause)) {
            throw new ConfigurationException(ExceptionContext.SECURITY, cause);
        } else if (certifier.isSevereError(code)) {
            log.error("Cannot connect to this server with Certifier {}. Proceed with another Certifier.", certifier.getClass().getName());
            return false;
        } else {
            log.error("Attempting less secure endpoint: ", ex);
            return true;
        }
    }
}
