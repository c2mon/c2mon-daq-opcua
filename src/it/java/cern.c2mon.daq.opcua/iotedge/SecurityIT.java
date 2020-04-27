package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.control.Endpoint;
import cern.c2mon.daq.opcua.control.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.opcua.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
@SpringBootTest(classes = {AppConfig.class})
@TestPropertySource(locations = "classpath:opcua.properties")
@ExtendWith(SpringExtension.class)
public class SecurityIT {

    private static ConnectionResolver resolver;
    private static String uri;
    private Endpoint endpoint;

    AppConfig config;

    @BeforeAll
    public static void startServer() {
        resolver = ConnectionResolver.resolveIoTEdgeServer();
        resolver.initialize();
        uri = resolver.getURI(ConnectionResolver.Ports.IOTEDGE);
    }

    @AfterAll
    public static void stopServer() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setUp() {
        config = TestUtils.createDefaultConfig();
    }

    @AfterEach
    public void cleanUp() {
        endpoint.reset();
        endpoint = null;
    }

    @Test
    public void connectWithoutCertificate() {
        initializeEndpoint();
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException {
        config.setEnableInsecureCommunication(false);
        trustAndConnect();
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException {
        setupAuthForCertificate();
        trustAndConnect();
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    private void initializeEndpoint() {
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        endpoint = new EndpointImpl(new MiloClientWrapperImpl(p), new TagSubscriptionMapperImpl(), new EventPublisher());
        endpoint.initialize(uri);
        endpoint.connect(false);
    }

    private void trustAndConnect() throws IOException, InterruptedException {
        log.info("Initial connection attempt.");
        try {
            this.initializeEndpoint();
        } catch (OPCCommunicationException e) {
            // expected behavior
        }
        resolver.trustCertificates();

        log.info("Reconnect.");
        this.initializeEndpoint();
        resolver.cleanUpCertificates();
    }

    private void setupAuthForCertificate(){
        config.setEnableInsecureCommunication(false);
        config.setEnableOnDemandCertification(false);
        String path = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
        config.getKeystore().setType("PKCS12");
        config.getKeystore().setPath(path);
        config.getKeystore().setAlias("1");
        config.getKeystore().setPwd("password");
    }

    private void setupAuthForPassword(){
        config.setEnableInsecureCommunication(true);
        config.setEnableOnDemandCertification(false);
        AppConfig.UsrPwdConfig usrPwdConfig = AppConfig.UsrPwdConfig.builder()
                .usr("user1")
                .pwd("password")
                .build();
        config.setUsrPwd(usrPwdConfig);
    }
}
