package cern.c2mon.daq.opcua.config;

import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class contains configuration options regarding the OPC UA DAQ connection and certification options and
 * optionally specific options for the respective certification option.
 */
@Configuration
@ConfigurationProperties(prefix = "c2mon.daq.opcua")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EquipmentScoped
public class AppConfigProperties {

    /**
     * A retry template to execute a call with a delay starting at retryDelay and increasing by a factor of retryDelay
     * on every failed attempt up to a maximum of maxFailoverDelay. Method calls are repeated disregarding the type of
     * exception.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate alwaysRetryTemplate() {
        AlwaysRetryPolicy retry = new AlwaysRetryPolicy();
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setMaxInterval(maxRetryDelay);
        backoff.setInitialInterval(retryDelay);
        backoff.setMultiplier(retryMultiplier);
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retry);
        template.setBackOffPolicy(backoff);
        return template;
    }

    /**
     * A retry template to execute a call with a delay starting at retryDelay and increasing by a factor of 2 on every
     * failed attempt up to a maximum of maxFailoverDelay.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate exceptionClassifierTemplate() {
        AlwaysRetryPolicy alwaysRetry = new AlwaysRetryPolicy();
        final NeverRetryPolicy neverRetry = new NeverRetryPolicy();
        final Map<Class<? extends Throwable>, RetryPolicy> policyMap = new ConcurrentHashMap<>();
        policyMap.put(CommunicationException.class, alwaysRetry);
        policyMap.put(OPCUAException.class, neverRetry);
        final ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
        retryPolicy.setPolicyMap(policyMap);
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setMaxInterval(maxRetryDelay);
        backoff.setInitialInterval(retryDelay);
        backoff.setMultiplier(retryMultiplier);
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backoff);
        return template;
    }
    /**
     * The Java class name of the a custom redundancy failover mode, if one shall be used. If specified, the default
     * query on an OPC UA server for its redundancy support information and proceeding and resolving the appropriate
     * failover mode. Must match exactly the Java class name of the class implementing {@link Controller}.
     */
    private FailoverMode redundancyMode;

    /**
     * If set, the client will not query a server its redundant server uri array upon initial connection, but use these
     * values as its redundant server set.
     */
    private List<String> redundantServerUris;

    /**
     * The delay before triggering a failover after a Session deactivates. Set to -1 to forego the Session status as a
     * trigger for a failover.
     */
    private long failoverDelay;

    /**
     * The publishing rate of the subscription for connection monitoring for redundant server sets in seconds.
     */
    private int connectionMonitoringRate = 3;

    /**
     * The delay before restarting the DAQ after an equipment change if the equipment addresses changed.
     */
    private long restartDelay = 2000L;

    /**
     * The delay before retrying a failed service call. The initial delay when retrying to recreate a subscription which
     * could not be transferred automatically, or a failed failover. In these cases, the time in between retries is
     * multiplied by retryMultiplier on every new failure, until reaching the maximum time of maxRetryDelay.
     */
    private long retryDelay = 5000L;

    /**
     * The maximum delay when retrying to recreate a subscription which could not be transferred automatically, or a
     * failed failover. The time in between retries is multiplied by retryMultiplier on every new failure, until
     * reaching the maximum time of maxRetryDelay.
     */
    private long maxRetryDelay = 10000L;

    /**
     * The multiplier on the delay time when retrying to recreate a subscription which could not be transferred
     * automatically, or a failed failover.
     */
    private long retryMultiplier = 2;

    /**
     * The maximum amount of attempts to retry service calls.
     */
    private int maxRetryAttempts = 1;

    /**
     * The timeout indicating for how long the client is willing to wait for a server response on a single transaction
     * in milliseconds.
     */
    private long requestTimeout;

    /**
     * The maximum number of values which can be queues in between publish intervals of the subscriptions. If more
     * updates occur during the timeframe of the DataTags' timedeadband, these values are added to the queue. If the
     * queueSize is 0, only the newest value is held in between publishing cycles.
     */
    private int queueSize;

    /**
     * The AliveWriter ensures that the SubEquipments connected to the OPC UA server are still running and sends regular
     * aliveTags to the C2MON Core.
     */
    private boolean aliveWriterEnabled;

    /**
     * If enabled, the client will make no attempt to validate server certificates, but trust servers. If disabled,
     * incoming server certificates are verified against the certificates listed in pkiBaseDir.
     */
    private boolean trustAllServers;

    /**
     * Specifies the path to the PKI directory of the client.  If the "trusted" subdirectory in pkiBaseDir contains
     * either a copy of either the incoming certificate or a copy of a certificate higher up the Certificate Chain, then
     * the certificate is deemed trustworthy.
     */
    private String pkiBaseDir;

    /**
     * Connection with a {@link cern.c2mon.daq.opcua.security.Certifier} associated with the element will be attempted
     * in decreasing order of the associated value until successful. If the value is 0 then that Certifier will not be
     * used. The entries are: "none" : {@link cern.c2mon.daq.opcua.security.NoSecurityCertifier} "generate" : {@link
     * cern.c2mon.daq.opcua.security.CertificateGenerator} "load" : {@link cern.c2mon.daq.opcua.security.CertificateLoader}.
     */
    private Map<CertifierMode, Integer> certifierPriority;


    /**
     * OPC UA servers are commonly configured to return a local host address in EndpointDescriptions returned on
     * discovery that may not be resolvable to the client (e.g. "127.0.0.1", or "opc.tcp://server:50" instead of
     * "opc.tcp://server.domain.ch:50".) Substituting the hostname allows administrators to handle such
     * (mis-)configurations of the server. The DAQ may append or substitute another hostname, where "global" refers to
     * the configured "globalHostName", while "local" uses the hostname within the address used for discovery.
     */
    private HostSubstitutionMode hostSubstitutionMode;

    /**
     * As with the "hostSubstitutionMode", the port can be substituted by the one of the address used for discovery, or
     * by the configured "globalPort".
     */
    private PortSubstitutionMode portSubstitutionMode;

    /**
     * The hostname to append of substitute if the "hostSubstitutionMode" is set to a global option. If the
     * "hostSubstitutionMode" is global but the "globalHostName" is not set, then the host is not substituted.
     */
    private String globalHostName;

    /**
     * The port to substitute if the "postSubstitutionMode" is GLOBAL.
     */
    private int globalPort;

    /**
     * The timestamp use for the generation of value updates. The Server reports a server timestamp, a source timestamp,
     * or both, where either may be null or misconfigured.
     */
    private TimeRecordMode timeRecordMode;

    // Settings to create a certificate, and to request connection to the server
    private String applicationName;
    private String applicationUri;
    private String organization;
    private String organizationalUnit;
    private String localityName;
    private String stateName;
    private String countryCode;

    private KeystoreConfig keystore = new KeystoreConfig();
    private PKIConfig pkiConfig = new PKIConfig();

    /**
     * Describes the modes offered by the OPC UA DAQ to modify the hostname in the address returned by the server with
     * the EndpointDescriptions on initial discovery. Possible modes are: NONE:              Don't modify the hostname
     * SUBSTITUTE_LOCAL:  Substitute the hostname in the EndpointDescription URI with the one used for discovery
     * APPEND_LOCAL:      Append the hostname of the address used for discovery to the one returned in the
     * EndpointDescription URI, separated with a dot '.' SUBSTITUTE_GLOBAL: Substitute the hostname in the
     * EndpointDescription URI with the "globalHostName" APPEND_GLOBAL:     Append the "globalHostName" to thehostname
     * in the EndpointDescription URI
     */
    @AllArgsConstructor
    public enum HostSubstitutionMode {
        NONE(false, false),
        SUBSTITUTE_LOCAL(false, true),
        APPEND_LOCAL(false, false),
        SUBSTITUTE_GLOBAL(true, true),
        APPEND_GLOBAL(true, false);
        boolean global;
        boolean substitute;
    }

    /**
     * Describes the modes offered by the OPC UA DAQ to substitute the port references in the address returned by the
     * server with the EndpointDescriptions on initial discovery. Possible modes are: NONE:   Don't modify the port
     * LOCAL:  Substitute the port in the EndpointDescription URI with the one of the address used for discovery (if
     * stated) GLOBAL: Substitute the port in the EndpointDescription URI with the "globalPort".
     */
    public enum PortSubstitutionMode {NONE, LOCAL, GLOBAL}

    /**
     * Describes the three modes of certifying a connection request offered by the OPC UA DAQ: using a  certificate
     * loaded from the local storage for connection, generating a self-signed certificate and attempting connection, or
     * connecting without signing or encrypting traffic.
     */
    public enum CertifierMode {LOAD, GENERATE, NO_SECURITY}

    /**
     * The four OPC UA Redundancy types. Add a custom value to add support for a vendor-proprietary redundancy setup.
     * */
    public enum FailoverMode { NONE, COLD, WARM, HOT, HOTANDMIRRORED }
    /**
     * Settings required to load an existing certificate from a keystore file
     */
    @Configuration
    @ConfigurationProperties(prefix = "c2mon.daq.opcua.keystore")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KeystoreConfig {
        private String type = "PKCS12";
        private String path;
        private String password = "";
        private String alias;
    }

    /**
     * Settings required to load an existing certificate from a PEM-encoded private key and certificate files
     */
    @Configuration
    @ConfigurationProperties(prefix = "c2mon.daq.opcua.pki")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PKIConfig {
        private String privateKeyPath;
        private String certificatePath;
    }
}
