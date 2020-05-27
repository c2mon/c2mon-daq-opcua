package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.connection.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.connection.SessionActivityListenerImpl;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
@SpringBootTest(classes = {AppConfig.class})
@TestPropertySource(locations = "classpath:opcua.properties")
@ExtendWith(SpringExtension.class)
public class SecurityIT {

    private static ConnectionResolver.Edge resolver;
    private Controller controller;

    AppConfig config;
    SecurityModule p;

    @BeforeAll
    public static void startServer() {
        resolver = ConnectionResolver.resolveIoTEdgeServer();
    }

    @AfterAll
    public static void stopServer() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setUp() {
        config = TestUtils.createDefaultConfig();
        p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());

    }

    @AfterEach
    public void cleanUp() throws CommunicationException, ConfigurationException {
        controller.stop();
        controller = null;
    }

    @Test
    public void connectWithoutCertificate() throws OPCUAException {
        initializeController();
        assertDoesNotThrow(()-> controller.isConnected());
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException {
        config.setInsecureCommunicationEnabled(false);
        trustAndConnect();
        assertDoesNotThrow(()-> controller.isConnected());
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException {
        setupAuthForCertificate();
        trustAndConnect();
        assertDoesNotThrow(()-> controller.isConnected());
    }

    private void initializeController() throws OPCUAException {
        controller = new ControllerImpl(new MiloEndpoint(p, new SessionActivityListenerImpl(), new EndpointSubscriptionListener()), new TagSubscriptionMapperImpl(), new TestListeners.TestListener());
        ((ControllerImpl)controller).setConfig(TestUtils.createDefaultConfig());
        controller.connect(resolver.getUri());
        controller.subscribeTags(Collections.singletonList(ServerTagFactory.DipData.createDataTag()));
    }

    private void trustAndConnect() throws IOException, InterruptedException, OPCUAException {
        log.info("Initial connection attempt.");
        try {
            this.initializeController();
        } catch (CommunicationException e) {
            // expected behavior
        }
        controller.stop();
        resolver.trustCertificates();

        log.info("Reconnect.");
        this.initializeController();
        resolver.cleanUpCertificates();
    }

    private void setupAuthForCertificate(){
        config.setInsecureCommunicationEnabled(false);
        config.setOnDemandCertificationEnabled(false);
        String path = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
        config.getKeystore().setType("PKCS12");
        config.getKeystore().setPath(path);
        config.getKeystore().setAlias("1");
        config.getKeystore().setPwd("password");
    }

    private void setupAuthForPassword(){
        config.setInsecureCommunicationEnabled(true);
        config.setOnDemandCertificationEnabled(false);
        AppConfig.UsrPwdConfig usrPwdConfig = AppConfig.UsrPwdConfig.builder()
                .usr("user1")
                .pwd("password")
                .build();
        config.setUsrPwd(usrPwdConfig);
    }
}
