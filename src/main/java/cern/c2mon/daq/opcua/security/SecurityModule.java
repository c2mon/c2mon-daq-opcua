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
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Cause.AUTH_ERROR;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Service
@Setter
@Slf4j
@RequiredArgsConstructor
public class SecurityModule {

    @Autowired
    private final AppConfig config;

    @Autowired
    @Qualifier("loader")
    private final CertificateLoader loader;

    @Autowired
    @Qualifier("generator")
    private final CertificateGenerator generator;

    private OpcUaClientConfigBuilder builder;

    public OpcUaClient createClient(List<EndpointDescription> endpoints) throws InterruptedException {
        OpcUaClient client = null;
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
        if (loader.canCertify()) {
            log.info("Authenticate with loaded certificate. ");
            client = connectIfPossible(endpoints, loader);
        }
        if (client == null && config.isEnableOnDemandCertification()) {
            log.info("Authenticate with generated certificate. ");
            client = connectIfPossible(endpoints, generator);
        }
        if (client == null && config.isEnableInsecureCommunication()) {
            log.info("Attempt insecure connection. ");
            client = connectIfPossible(endpoints, e -> e.getSecurityLevel().intValue() == 0, generator);
            log.info("Insecure connection unsuccessful. ");
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
        return connectIfPossible(endpoints, e -> supportsSigAlg(e, certifier), certifier);
    }

    private OpcUaClient connectIfPossible(List<EndpointDescription> endpoints,
                                          Function<EndpointDescription, Boolean> predicate,
                                          Certifier certifier) throws InterruptedException {
        final var matchingEndpoints = endpoints.stream().filter(predicate::apply).collect(Collectors.toList());
        for (EndpointDescription e : matchingEndpoints) {
            try {
                log.info("Attempt authentication with mode {} and algorithm {}. ", e.getSecurityMode(), e.getSecurityPolicyUri());
                certifier.certify(builder);
                builder.setEndpoint(e);
                OpcUaClient client = OpcUaClient.create(builder.build());
                return (OpcUaClient) client.connect().get();
            } catch (UaException | ExecutionException ex) {
                log.error("Authentication error. Attempting less secure endpoint: ", ex);
            }
        }
        return null;
    }

    private boolean supportsSigAlg(EndpointDescription e, Certifier certifier) {
        final var sp = SecurityPolicy.fromUriSafe(e.getSecurityPolicyUri());
        return sp.isPresent() && certifier.supports(sp.get().getAsymmetricSignatureAlgorithm().getTransformation());
    }
}
