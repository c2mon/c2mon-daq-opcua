package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.failover.Controller;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

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
     * failover mode. Must match exactly the Java class name of the class implementing {@link
     * Controller}.
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
     * The delay before restarting the DAQ after an equipment change.
     */
    private long restartDelay = 2000L;

    /**
     * The time to wait before retrying a failed attempt of individual methods executed on the OPC UA SDK, or of
     * recreating a subscription which could not be transferred automatically to a new session in milliseconds.
     */
    private long retryDelay = 50000L;

    /**
     * If the redundant server is not yet available when the active server goes down, a retry is started after the
     * retryDelay period. The time in between retries is duplicated on every new failure, until reaching the maximum
     * time of maxFailoverDelay.
     */
    private long maxFailoverDelay = 10000L;

    /**
     * How often to retry connecting to an unavailable server when connectToDataSource() is called?
     */
    private int maxRetryAttempts = 1;

    /**
     * The timeout indicating for how long the client is willing to wait for a server response on a single transaction
     * in milliseconds. The maximum value is 5000 due to a static timeout in the Eclipse Milo package.
     */
    private long timeout;

    /**
     * The AliveWriter ensures that the SubEquipments connected to the OPC UA server are still running and sends regular
     * aliveTags to the C2MON Core
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
     * in order or magnitude of the value, starting with the highest value. If the value is not given then that
     * Certifier will not be used. The entries map the the Certifies as follows: "none" : {@link
     * cern.c2mon.daq.opcua.security.NoSecurityCertifier} "generate" : {@link cern.c2mon.daq.opcua.security.CertificateGenerator}
     * "load" : {@link cern.c2mon.daq.opcua.security.CertificateLoader}
     */
    private Map<String, Integer> certificationPriority;

    // Settings to create a certificate, and to request connection to the server
    private String appName;
    private String productUri;
    private String applicationUri;
    private String organization;
    private String organizationalUnit;
    private String localityName;
    private String stateName;
    private String countryCode;
    private int requestTimeout;

    private KeystoreConfig keystore = new KeystoreConfig();
    private UsrPwdConfig usrPwd = new UsrPwdConfig();
    private PKIConfig pkiConfig = new PKIConfig();

    /**
     * Settings required to load an existing certificate from a keystore file
     */
    @Configuration
    @ConfigurationProperties(prefix = "app.keystore")
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KeystoreConfig {
        private String type = "PKCS12";
        private String path;
        private String pwd = "";
        private String alias;
    }

    /**
     * Settings required to load an existing certificate from a PEM-encoded private key and certificate files
     */
    @Configuration
    @ConfigurationProperties(prefix = "app.pki")
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PKIConfig {
        private String pkPath;
        private String crtPath;
    }

    /**
     * Settings required for authentication with username and password
     */
    @Configuration
    @ConfigurationProperties(prefix = "app.usr")
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UsrPwdConfig {
        private String usr;
        private String pwd;
    }
}
