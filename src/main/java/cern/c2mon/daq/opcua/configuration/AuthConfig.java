package cern.c2mon.daq.opcua.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuthConfig {
    private boolean communicateWithoutSecurity = false;

    private String keyStoreType = "PKCS12";
    private String keyStorePath;
    private String keyStorePassword;
    private String keyStoreAlias;

    private String certificateBaseDir;
    private String privateKeyPassword;
}
