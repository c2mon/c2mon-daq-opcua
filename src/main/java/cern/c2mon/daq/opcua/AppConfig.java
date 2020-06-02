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
    private int maxRetryAttemps = 1;

    /**
     * The timeout inidcating for how long the client is willing to wait for a server response on a single transaction
     * in milliseconds. The maximum value is 5000 due to a static timeout in the Eclipse Milo package.
     */
    private int timout;


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

    private KeystoreConfig keystore = new KeystoreConfig();
    private UsrPwdConfig usrPwd = new UsrPwdConfig();

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
