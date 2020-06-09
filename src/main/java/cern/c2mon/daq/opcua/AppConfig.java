package cern.c2mon.daq.opcua;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

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
public class AppConfig {

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
     * How often to retry connecting to an unavailable server when connectToDataSource() is called?
     */
    private int maxRetryAttempts = 1;

    /**
     * The timeout indicating for how long the client is willing to wait for a server response on a single transaction
     * in milliseconds. The maximum value is 5000 due to a static timeout in the Eclipse Milo package.
     */
    private int timeout;


    private String appName;
    private String productUri;
    private String applicationUri;
    private String organization;
    private String organizationalUnit;
    private String localityName;
    private String stateName;
    private String countryCode;
    private int requestTimeout;

    /**
     * If insecure communication is enabled, the client attempts to connect to OPC UA endpoints without security as a
     * fallback.
     */
    private boolean insecureCommunicationEnabled = true;

    /**
     * If on demand certification is enabled, the client creates and attempts connection with a self-signed certificate
     * if no certificate could be loaded.
     */
    private boolean onDemandCertificationEnabled = true;

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

    private KeystoreConfig keystore = new KeystoreConfig();
    private UsrPwdConfig usrPwd = new UsrPwdConfig();
    private PKIConfig pkiConfig = new PKIConfig();

    /**
     * Settings required to load an existing certificate
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
     * Settings required to load an existing certificate
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
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UsrPwdConfig {
        private String usr;
        private String pwd;
    }
}
