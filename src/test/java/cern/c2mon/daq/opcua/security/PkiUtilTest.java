package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.iotedge.SecurityIT;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PkiUtilTest {

    @Test
    void testLoadPkFromFile() throws ConfigurationException {
        final String pkPath = SecurityIT.class.getClassLoader().getResource("pkcs8server.key").getPath();
        final PrivateKey privateKey = PkiUtil.loadPrivateKey(pkPath);
        assertNotNull(privateKey);
    }

    @Test
    void loadPKCS1ShouldThrowException() {
        final String pkPath = SecurityIT.class.getClassLoader().getResource("server.key").getPath();
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadPrivateKey(pkPath));
    }

    @Test
    void testLoadCertificateFromFile() throws ConfigurationException {
        final String pkPath = SecurityIT.class.getClassLoader().getResource("server.crt").getPath();
        final X509Certificate privateKey = PkiUtil.loadCertificate(pkPath);
        assertNotNull(privateKey);
    }

}
