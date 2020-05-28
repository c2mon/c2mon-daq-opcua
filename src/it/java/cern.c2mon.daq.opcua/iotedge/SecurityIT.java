package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.connection.*;
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
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest
@TestPropertySource(locations = "classpath:opcua.properties")
@ExtendWith(SpringExtension.class)
public class SecurityIT {

    private static ConnectionResolver.Edge resolver;

    @Autowired
    Controller controller;

    @Autowired
    AppConfig config;

    @Autowired
    Endpoint endpoint;

    private final TestListeners.TestListener listener = new TestListeners.TestListener();

    @BeforeAll
    public static void startServers() {
        resolver = ConnectionResolver.resolveIoTEdgeServer();
    }

    @AfterAll
    public static void stopServer() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(controller, "endpointListener", listener);
        ReflectionTestUtils.setField(endpoint, "listener", listener);
        config.setMaxInitialRetryAttempts(1);
        listener.reset();
    }

    @AfterEach
    public void cleanUp() {
        controller.stop();
        controller = null;
    }

    @Test
    public void connectWithoutCertificate() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        final var f = listener.getStateUpdate();
        initializeController();
        assertEquals(EndpointListener.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.setInsecureCommunicationEnabled(false);
        final var f = listener.getStateUpdate();
        trustAndConnect();
        assertEquals(EndpointListener.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        final var f = listener.getStateUpdate();
        setupAuthForCertificate();
        trustAndConnect();
        assertEquals(EndpointListener.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    private void initializeController() throws OPCUAException, InterruptedException {
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
