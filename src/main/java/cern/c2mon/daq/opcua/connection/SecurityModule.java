package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.security.Certifier;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Chooses an endpoint to connect to and creates a client as configured in {@link AppConfig}, selecting from a list
 * of endpoints.
 */
@Component("securityModule")
@Setter
@Slf4j
@RequiredArgsConstructor
public class SecurityModule {

    private final AppConfig config;

    private final Certifier loader;

    private final Certifier generator;

    private final Certifier noSecurity;

    private OpcUaClientConfigBuilder builder;

    /**
     * Creates an {@link OpcUaClient} according to the configuration specified in {@link AppConfig} and connect to it.
     * The endpoint to connect to is chosen according to the security policy. Preference is given to endpoints to which
     * a connection can be made using a certificate loaded from a keystore file, is so configured. If this is not
     * possible and on-the-fly certificate generation is enabled, self-signed certificates will be generated to attempt
     * connection with appropriate endpoints. Connection with an insecure endpoint are attempted only if this is not
     * possible either and the option is allowed in the configuration.
     *
     * @param endpoints A list of endpoints of which to connect to one.
     * @return An {@link OpcUaClient} object that is connected to one of the endpoints.
     * @throws InterruptedException if interrupt was called on the thread during execution.
     */
    public OpcUaClient createClientWithListener(List<EndpointDescription> endpoints, SessionActivityListener listener) throws InterruptedException, CommunicationException, ConfigurationException {
        builder = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english(config.getAppName()))
                .setApplicationUri(config.getApplicationUri())
                .setRequestTimeout(uint(config.getRequestTimeout()));

        if (isUsrPwdConfigured(config.getUsrPwd())) {
            log.info("Authenticating as user {}. ", config.getUsrPwd().getUsr());
            UsernameProvider p = new UsernameProvider(config.getUsrPwd().getUsr(), config.getUsrPwd().getPwd());
            builder.setIdentityProvider(p);
        }
        // assure that endpoints is a mutable list
        endpoints = new ArrayList<>(endpoints);

        // prefer more secure endpoints
        endpoints.sort(Comparator.comparing(EndpointDescription::getSecurityLevel).reversed());

        //connect with existing certificate if present
        OpcUaClient client = connectIfPossible(endpoints, loader, listener);

        if (client == null && config.isOnDemandCertificationEnabled()) {
            log.info("Authenticate with generated certificate. ");
            client = connectIfPossible(endpoints, generator, listener);
        }
        if (client == null && config.isInsecureCommunicationEnabled()) {
            log.info("Attempt insecure connection. ");
            client = connectIfPossible(endpoints, noSecurity, listener);
        }
        if (client == null) {
            throw new CommunicationException(ExceptionContext.AUTH_ERROR);
        }
        log.info("Connected successfully! ");
        return client;
    }

    private boolean isUsrPwdConfigured(AppConfig.UsrPwdConfig upConfig) {
        return upConfig != null &&
                !Strings.isNullOrEmpty(upConfig.getUsr()) &&
                !Strings.isNullOrEmpty(upConfig.getPwd());
    }

    private OpcUaClient connectIfPossible(List<EndpointDescription> endpoints, Certifier certifier, SessionActivityListener listener) throws InterruptedException, ConfigurationException {
        final var matchingEndpoints = endpoints.stream().filter(certifier::supportsAlgorithm).collect(Collectors.toList());
        for (var e : matchingEndpoints) {
            if (!certifier.canCertify(e)) {
                log.info("Unable to certify endpoint {}, {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
                continue;
            }
            certifier.certify(builder, e);
            log.info("Attempt authentication with mode {} and algorithm {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
            try {
                OpcUaClient client = OpcUaClient.create(builder.build());
                client.addSessionActivityListener(listener);
                return (OpcUaClient) client.connect().get();
            } catch (UaException ex) {
                log.error("Unsupported transport in endpoint URI. Attempting less secure endpoint", ex);
                endpoints.remove(e);
                certifier.uncertify(builder);
            } catch (ExecutionException ex) {
                if (!handleAndReportWhetherToContinue(certifier, ex)) {
                    break;
                }
                certifier.uncertify(builder);
            }
        }
        return null;
    }

    private boolean handleAndReportWhetherToContinue(Certifier certifier, ExecutionException ex) throws ConfigurationException {
        final var cause = ex.getCause();
        log.error("Authentication error: ", cause);
        if (!(cause instanceof UaException)) {
            log.error("Unexpected error in connection, abort connecting through certifier {}.", certifier.getClass().getName());
            return false;
        }
        final var code = ((UaException) cause).getStatusCode().getValue();
        StatusCodes.lookup(code).ifPresentOrElse(
                s -> log.error("Failure description: {}", Arrays.toString(s)),
                () -> log.error("Reasons unknown."));
        if (OPCUAException.isSecurityIssue((UaException) cause)) {
            log.info("Ensure configuration correctness.");
            throw new ConfigurationException(ExceptionContext.SECURITY);
        } else if (certifier.getSevereErrorCodes().contains(code)) {
            log.error("Cannot connect to this server with certifier {}. Proceed with another certifier.", certifier.getClass().getName());
            return false;
        }
        log.error("Attempting less secure endpoint: ", ex);
        return true;
    }
}
