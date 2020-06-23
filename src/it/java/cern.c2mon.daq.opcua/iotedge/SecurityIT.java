package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@SpringBootTest
@TestPropertySource(locations = "classpath:securityIT.properties")
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SecurityIT {

    private static ConnectionResolver.OpcUaImage.Edge edge;

    @Autowired
    Controller controller;

    @Autowired
    AppConfig config;

    @Autowired
    FailoverProxy testFailoverProxy;

    @Autowired TestListeners.TestListener testListener;

    @BeforeAll
    public static void startServers() {
        edge = new ConnectionResolver.OpcUaImage.Edge();
        edge.startStandAlone();
    }

    @AfterAll
    public static void stopServer() {
        edge.close();
    }

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(controller, "endpointListener", testListener);
        ReflectionTestUtils.setField(controller, "failoverProxy", testFailoverProxy);
        ReflectionTestUtils.setField(testFailoverProxy, "endpointListener", testListener);
        testListener.reset();
    }

    @AfterEach
    public void cleanUp() throws IOException, InterruptedException {
        config.setTrustAllServers(true);
        config.getCertificationPriority().put("none", 1);
        config.getCertificationPriority().put("generate", 2);
        config.getCertificationPriority().put("load", 3);
        edge.cleanUpCertificates();
        FileUtils.deleteDirectory(new File(config.getPkiBaseDir()));
        final var f = testListener.listen();
        controller.stop();
        try {
            f.get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | CompletionException | ExecutionException e) {
            // assure that server has time to disconnect, fails for test on failed connections
        }
        controller = null;
    }

    @Test
    public void shouldConnectWithoutCertificateIfOthersFail() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        final var f = testListener.getStateUpdate();
        controller.connect(edge.getUri());
        controller.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
        assertEquals(EndpointListener.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("load");
        final var f = trustCertificatesOnServerAndConnect();
        assertEquals(EndpointListener.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("generate");
        final var f = trustCertificatesOnServerAndConnect();
        assertEquals(EndpointListener.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void activeKeyChainValidatorShouldFail() {
        config.setTrustAllServers(false);
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("generate");
        assertThrows(CommunicationException.class, this::trustCertificatesOnServerAndConnect);
    }

    @Test
    public void serverCertificateInPkiFolderShouldSucceed() throws InterruptedException, OPCUAException, IOException, TimeoutException, ExecutionException {
        config.setTrustAllServers(false);
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("generate");

        try {
            trustCertificatesOnServerAndConnect();
        } catch (CommunicationException e) {
            // expected behavior: rejected by the client
        }

        trustCertificatesOnClient();

        final var state = testListener.listen();
        controller.connect(edge.getUri());
        assertEquals(EndpointListener.EquipmentState.OK, state.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    private void trustCertificatesOnClient() throws IOException {
        final File[] files = new File(config.getPkiBaseDir() + "/rejected").listFiles();
        for (File file : files) {
            log.info("Trusting Certificate {}. ", file.getName());
            Files.move(file, new File(config.getPkiBaseDir() + "/trusted/certs/" + file.getName()));
        }
    }

    private CompletableFuture<EndpointListener.EquipmentState> trustCertificatesOnServerAndConnect() throws IOException, InterruptedException, OPCUAException {
        log.info("Initial connection attempt...");
        try {
            controller.connect(edge.getUri());
        } catch (CommunicationException e) {
            // expected behavior: rejected by the server
        }
        controller.stop();

        log.info("Trust certificates server-side and reconnect...");
        edge.trustCertificates();
        final var state = testListener.listen();

        controller.connect(edge.getUri());
        controller.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
        return state;
    }
}
