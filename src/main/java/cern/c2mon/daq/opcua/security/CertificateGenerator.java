package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Component
@Slf4j
@Getter
@RequiredArgsConstructor
@Qualifier("generator")
public class CertificateGenerator extends Certifier {
    final AppConfig config;

    private static final String[] SUPPORTED_SIG_ALGS = {"SHA256withRSA"};

    protected void process() {
        log.info("Generating self-signed certificate and keypair.");
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                    .setCommonName(config.getAppName())
                    .setOrganization(config.getOrganization())
                    .setOrganizationalUnit(config.getOrganizationalUnit())
                    .setLocalityName(config.getLocalityName())
                    .setStateName(config.getStateName())
                    .setCountryCode(config.getCountryCode())
                    .setApplicationUri(config.getApplicationUri());
            certificate = builder.build();
        } catch (NoSuchAlgorithmException e) {
            log.error("Could not generate RSA keypair");
        } catch (Exception e) {
            log.error("Could not generate certificate");
        }
    }

    public boolean supports(String sigAlg) {
        return Arrays.stream(SUPPORTED_SIG_ALGS).anyMatch(s -> s.equalsIgnoreCase(sigAlg));
    }
}
