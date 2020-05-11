package cern.c2mon.daq.opcua;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * This class contains configuration options regarding the OPC UA DAQ connection and certification options and optionally
 * specific options for the respective certification option.
 */
@Configuration
@ConfigurationProperties(prefix="app")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppConfig {
    private String appName;
    private String productUri;
    private String applicationUri;
    private String organization;
    private String organizationalUnit;
    private String localityName;
    private String stateName;
    private String countryCode;
    private int requestTimeout;
    private boolean failFast;

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
    @ConfigurationProperties(prefix="app.keystore")
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
