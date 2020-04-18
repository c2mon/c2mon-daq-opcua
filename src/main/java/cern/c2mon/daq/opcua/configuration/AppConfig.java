package cern.c2mon.daq.opcua.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app")
@Getter
@Setter
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

    private KeystoreConfig keystore;
    private UsrPwdConfig usrPwd;

    /**
     * Mandatory settings to load an existing certificate
     */
    @Getter
    @Setter
    @Builder
    public static class KeystoreConfig {
        private String type = "PKCS12";
        private String path;
        private String pwd = "";
        private String alias;
        private String pkPwd;
    }

    /**
     * Mandatory settings for authentication with username and password
     */
    @Getter
    @Setter
    @Builder
    public static class UsrPwdConfig {
        private String usr;
        private String pwd;
    }
}
