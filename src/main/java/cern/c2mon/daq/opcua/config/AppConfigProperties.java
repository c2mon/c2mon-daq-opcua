package cern.c2mon.daq.opcua.config;

import cern.c2mon.daq.opcua.control.Controller;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.Map;

/**
 * This class contains configuration options regarding the OPC UA DAQ connection and certification options and
 * optionally specific options for the respective certification option.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@EnableRetry
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppConfigProperties {

    /**
     * The Java class name of the a custom redundancy failover mode, if one shall be used. If specified, the default
     * query on an OPC UA server for its redundancy support information and proceeding and resolving the appropriate
     * failover mode. Must match exactly the Java class name of the class implementing {@link Controller}.
     */
    private String redundancyMode;

    /**
     * If set, the client will not query a server its redundant server uri array upon initial connection, but use these
     * values as its redundant server set.
     */
    private List<String> redundantServerUris;

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
     * duplicated on every new failure, until reaching the maximum time of maxRetryDelay.
     */
    private long retryDelay = 50000L;

    /**
     * The maximum delay when retrying to recreate a subscription which could not be transferred automatically, or a
     * failed failover. The time in between retries is duplicated on every new failure, until reaching the maximum time
     * of maxRetryDelay.
     */
    private long maxRetryDelay = 10000L;

    /**
     * The maximum amount of attempts to retry service calls
     */
    private int maxRetryAttempts = 1;

    /**
     * The timeout indicating for how long the client is willing to wait for a server response on a single transaction
     * in milliseconds.
     */
    private long requestTimeout;

    /**
     * The maximum number of values which can be queues in between publish intervals of the subscriptions. If more
     * updates occur during the timeframe of the DataTags' timedeadband, these values are added to the queue. The
     * fastest possible sampling rate for the server is used for each MonitoredItem.
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
     * in decreasing order of the associated value until successful. If the value is not given then that Certifier will
     * not be used. The entries are:
     * "none" : {@link cern.c2mon.daq.opcua.security.NoSecurityCertifier}
     * "generate" : {@link cern.c2mon.daq.opcua.security.CertificateGenerator}
     * "load" : {@link cern.c2mon.daq.opcua.security.CertificateLoader}
     */
    private Map<String, Integer> certificationPriority;

    // Settings to create a certificate, and to request connection to the server
    private String appName;
    private String applicationUri;
    private String organization;
    private String organizationalUnit;
    private String localityName;
    private String stateName;
    private String countryCode;

    private KeystoreConfig keystore = new KeystoreConfig();
    private PKIConfig pkiConfig = new PKIConfig();

    /**
     * A retry template to execute a call with a delay starting at retryDelay and increasing by a factor of 2 on every
     * failed attempt up to a maximum of maxFailoverDelay.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate exponentialDelayTemplate() {
        AlwaysRetryPolicy retry = new AlwaysRetryPolicy();
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setMaxInterval(maxRetryDelay);
        backoff.setInitialInterval(retryDelay);
        backoff.setMultiplier(2);
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retry);
        template.setBackOffPolicy(backoff);
        return template;
    }

    /**
     * Settings required to load an existing certificate from a keystore file
     */
    @Configuration
    @ConfigurationProperties(prefix = "app.keystore")
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
    @ConfigurationProperties(prefix = "app.pki")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PKIConfig {
        private String privateKeyPath;
        private String certificatePath;
    }
}
