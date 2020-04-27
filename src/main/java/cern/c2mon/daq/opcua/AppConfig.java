package cern.c2mon.daq.opcua;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="app")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/**
 * Configurations regarding the application. Note that these configurations will be used when creating self-signing
 * certificates.
 */
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
    private boolean enableInsecureCommunication = true;
    private boolean enableOnDemandCertification = true;

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
