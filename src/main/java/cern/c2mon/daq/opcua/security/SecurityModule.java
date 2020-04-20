package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Cause.AUTH_ERROR;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Service
@Setter
@Slf4j
@RequiredArgsConstructor

/**
 * Chooses an endpoint to connect to and creates a client as configured in {@link AppConfig}, selecting from a list
 * of endpoints.
 */
public class SecurityModule {

    @Autowired
    private final AppConfig config;

    @Autowired
    @Qualifier("loader")
    private final Certifier loader;

    @Autowired
    @Qualifier("generator")
    private final Certifier generator;

    @Autowired
    @Qualifier("none")
    private final Certifier noSecurityCertifier;

    private OpcUaClientConfigBuilder builder;

    /**
     * Creates an {@link OpcUaClient} according to the configuration specified in {@link AppConfig} and connect to it.
     * The endpoint to connect to is chosen according to the security policy. Preference is given to endpoints to which
     * a connection can be made using a certificate loaded from a keystore file, is so configured. If this is not
     * possible and on-the-fly certificate generation is enabled, self-signed certificates will be generated to attempt
     * connection with appropriate endpoints. Connection with an insecure endpoint are attempted only if this is not
     * possible either and the option is allowed in the configuration.
     * @param endpoints A list of endpoints of which to connect to one.
     * @return An {@link OpcUaClient} object that is connected to one of the endpoints.
     * @throws InterruptedException if interrupt was called on the thread during execution.
     */
    public OpcUaClient createClient(List<EndpointDescription> endpoints) throws InterruptedException {
        builder = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english(config.getAppName()))
                .setApplicationUri(config.getApplicationUri())
                .setRequestTimeout(uint(config.getRequestTimeout()));

        if (isUsrPwdConfigured(config.getUsrPwd())) {
            log.info("Authenticating as user {}. ", config.getUsrPwd().getUsr());
            UsernameProvider p = new UsernameProvider(config.getUsrPwd().getUsr(), config.getUsrPwd().getPwd());
            builder.setIdentityProvider(p);
        }

        // prefer more secure endpoints
        endpoints.sort(Comparator.comparing(EndpointDescription::getSecurityLevel).reversed());

        //connect with existing certificate if present
        OpcUaClient client = connectIfPossible(endpoints, loader);

        if (client == null && config.isEnableOnDemandCertification()) {
            log.info("Authenticate with generated certificate. ");
            client = connectIfPossible(endpoints, generator);
        }
        if (client == null && config.isEnableInsecureCommunication()) {
            log.info("Attempt insecure connection. ");
            client = connectIfPossible(endpoints, noSecurityCertifier);
        }
        if (client == null) {
            throw new OPCCommunicationException(AUTH_ERROR);
        }
        return client;
    }

    private boolean isUsrPwdConfigured(AppConfig.UsrPwdConfig upConfig) {
        return upConfig != null &&
                !Strings.isNullOrEmpty(upConfig.getUsr()) &&
                !Strings.isNullOrEmpty(upConfig.getPwd());
    }

    private OpcUaClient connectIfPossible(List<EndpointDescription> endpoints, Certifier certifier) throws InterruptedException {
        final List<EndpointDescription> matchingEndpoints = endpoints.stream().filter(certifier::supportsAlgorithm).collect(Collectors.toList());
        for (EndpointDescription e : matchingEndpoints) {
            if (certifier.canCertify(e)) {
                certifier.certify(builder, e);
                log.info("Attempt authentication with mode {} and algorithm {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
                try {
                    OpcUaClient client = OpcUaClient.create(builder.build());
                    return (OpcUaClient) client.connect().get();
                } catch (UaException | ExecutionException ex) {
                    log.error("Authentication error. Attempting less secure endpoint: ", ex);
                }
            }
            else {
                log.info("Unable to certify endpoint {}, {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
            }
        }
        return null;
    }
}
