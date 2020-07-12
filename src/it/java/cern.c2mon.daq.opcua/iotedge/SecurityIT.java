package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.control.TagController;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.failover.NoFailover;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:securityIT.properties")
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SecurityIT {


    @Autowired AppConfigProperties config;
    @Autowired FailoverProxy failoverProxy;
    @Autowired TagController controller;
    @Autowired FailoverProxy testFailoverProxy;
    @Autowired NoFailover noFailover;
    @Autowired TestListeners.Pulse listener;

    private String uri;

    @Container
    public GenericContainer active = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
            .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
            .withCommand("--unsecuretransport")
            .withExposedPorts(EdgeTestBase.IOTEDGE);

    @BeforeEach
    public void setUp() {
        uri = "opc.tcp://" + active.getContainerIpAddress() + ":" + active.getFirstMappedPort();
        ReflectionTestUtils.setField(failoverProxy, "messageSender", listener);
        ReflectionTestUtils.setField(controller, "messageSender", listener);
        ReflectionTestUtils.setField(controller, "failoverProxy", testFailoverProxy);
        ReflectionTestUtils.setField(noFailover.currentEndpoint(), "messageSender", listener);
        listener.reset();
    }

    @AfterEach
    public void cleanUp() throws IOException, InterruptedException {
        config.setTrustAllServers(true);
        config.getCertificationPriority().put("none", 1);
        config.getCertificationPriority().put("generate", 2);
        config.getCertificationPriority().put("load", 3);
        cleanUpCertificates();
        FileUtils.deleteDirectory(new File(config.getPkiBaseDir()));
        final var f = listener.listen();
        failoverProxy.stop();
        try {
            f.get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | CompletionException | ExecutionException e) {
            // assure that server has time to disconnect, fails for test on failed connections
        }
        controller = null;
    }

    @Test
    public void shouldConnectWithoutCertificateIfOthersFail() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        final var f = listener.getStateUpdate();
        failoverProxy.connect(uri);
        controller.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
        assertEquals(MessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("load");
        final var f = trustCertificatesOnServerAndConnect();
        assertEquals(MessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("generate");
        final var f = trustCertificatesOnServerAndConnect();
        assertEquals(MessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
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

        final var state = listener.listen();
        failoverProxy.connect(uri);
        assertEquals(MessageSender.EquipmentState.OK, state.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    private void trustCertificatesOnClient() throws IOException {
        final File[] files = new File(config.getPkiBaseDir() + "/rejected").listFiles();
        for (File file : files) {
            log.info("Trusting Certificate {}. ", file.getName());
            Files.move(file, new File(config.getPkiBaseDir() + "/trusted/certs/" + file.getName()));
        }
    }

    private CompletableFuture<MessageSender.EquipmentState> trustCertificatesOnServerAndConnect() throws IOException, InterruptedException, OPCUAException {
        log.info("Initial connection attempt...");
        try {
            failoverProxy.connect(uri);
        } catch (CommunicationException e) {
            // expected behavior: rejected by the server
        }
        failoverProxy.stop();

        log.info("Trust certificates server-side and reconnect...");
        trustCertificates();
        final var state = listener.listen();

        failoverProxy.connect(uri);
        controller.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
        return state;
    }



    private void trustCertificates() throws IOException, InterruptedException {
        log.info("Trust certificate.");
        active.execInContainer("mkdir", "pki/trusted");
        active.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");
    }

    private void cleanUpCertificates() throws IOException, InterruptedException {
        log.info("Cleanup.");
        active.execInContainer("rm", "-r", "pki/trusted");
    }
}
