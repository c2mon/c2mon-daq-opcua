package cern.c2mon.daq.opcua.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app")
@Getter
@Setter
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

    private AuthConfig auth;
}
