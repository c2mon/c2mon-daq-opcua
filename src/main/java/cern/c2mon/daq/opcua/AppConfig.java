package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.classify.Classifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import javax.annotation.PostConstruct;

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

    /**
     * Set to -1 to retry indefinitely
     */
    private int maxRetryAttempts = -1;
    private int retryDelay = 50000;

    /**
     * How often to retry connecting to an unavailable server when connectToDataSource() is called?
     */
    private int maxInitialRetryAttempts = 1;

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

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(getRetryDelay());
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        if (getMaxRetryAttempts() == -1) {
            retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        } else {
            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
            // Fall back to 1 if invalid
            retryPolicy.setMaxAttempts(Math.max(getMaxRetryAttempts(), 1));
            retryTemplate.setRetryPolicy(retryPolicy);
        }

        return retryTemplate;
    }

    @Bean
    public RetryTemplate initialRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(getRetryDelay());
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        // Fall back to 1 if maxInitialRetryAttempts invalid
        ExceptionClassifierRetryPolicy retryPolicy = new InitialRetryPolicy(Math.max(getMaxInitialRetryAttempts(), 1));
        retryTemplate.setRetryPolicy(retryPolicy);
        return retryTemplate;
    }

    /**
     * Don't attempt reconnection if a configuration error is thrown
     */
    private static class InitialRetryPolicy extends ExceptionClassifierRetryPolicy {

        InitialRetryPolicy(int maxInitialAttempt) {
            final SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
            simpleRetryPolicy.setMaxAttempts(maxInitialAttempt);
            this.setExceptionClassifier((Classifier<Throwable, RetryPolicy>) classifiable -> {
                if (classifiable instanceof ConfigurationException) {
                    return new NeverRetryPolicy();
                }
                return simpleRetryPolicy;
            });
        }
    }
}
