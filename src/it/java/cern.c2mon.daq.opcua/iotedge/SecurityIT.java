package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.SecurityModule;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
@SpringBootTest(classes = {AppConfig.class})
@TestPropertySource(locations = "classpath:opcua.properties")
@ExtendWith(SpringExtension.class)
public class SecurityIT {

    private Endpoint endpoint;
    private static GenericContainer image;
    private static String uri;

    SecurityModule p;
    AppConfig config;

    @BeforeAll
    public static void startServer() {
       image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        image.start();
        int PORT = 50000;
        uri = "opc.tcp://" + image.getContainerIpAddress() + ":" + PORT;
    }

    @AfterAll
    public static void stopServer() {
        image.stop();
        image.close();
    }

    @BeforeEach
    public void setUp() {
        config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .enableInsecureCommunication(true)
                .enableOnDemandCertification(true)
                .build();
        p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config));
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

    @Test
    public void authWithUserNameAndPasswordShouldConnect() {
        setupAuthForPassword();
        initializeEndpoint("opc.tcp://milo.digitalpetri.com:62541/milo");
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    private void initializeEndpoint() {
        initializeEndpoint(uri);
    }

    private void initializeEndpoint(String uri) {
        MiloClientWrapper wrapper = new MiloClientWrapperImpl();
        wrapper.initialize(uri);
        wrapper.setSecurityModule(p);
        wrapper.setConfig(config);
        endpoint = new EndpointImpl(wrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
        endpoint.initialize(false);
    }

    private void trustAndConnect() throws IOException, InterruptedException {
        log.info("Initial connection attempt.");
        try {
            this.initializeEndpoint();
        } catch (OPCCommunicationException e) {
            // expected behavior
        }

        log.info("Trust certificate.");
        image.execInContainer("mkdir", "pki/trusted");
        image.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");

        log.info("Reconnect.");
        this.initializeEndpoint();

        log.info("Cleanup.");
        image.execInContainer("rm", "-r", "pki/trusted");
    }

    private void setupAuthForCertificate(){
        config.setEnableInsecureCommunication(false);
        String path = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
        AppConfig.KeystoreConfig keystoreConfig = AppConfig.KeystoreConfig.builder()
                .type("PKCS12")
                .path(path)
                .alias("1")
                .pwd("password")
                .pkPwd("password")
                .build();
        config.setKeystore(keystoreConfig);
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
