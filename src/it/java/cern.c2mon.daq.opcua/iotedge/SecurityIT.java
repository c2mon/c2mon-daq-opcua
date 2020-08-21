package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static cern.c2mon.daq.opcua.config.AppConfigProperties.CertifierMode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:securityIT.properties")
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SecurityIT {


    @Autowired AppConfigProperties config;
    @Autowired Controller controllerProxy;
    @Autowired IDataTagHandler tagHandler;
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
        ReflectionTestUtils.setField(tagHandler, "messageSender", listener);
        ReflectionTestUtils.setField(tagHandler, "controller", controllerProxy);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", listener);
        listener.reset();
    }

    @AfterEach
    public void cleanUp() throws IOException, InterruptedException {
        config.setTrustAllServers(true);
        config.getCertifierPriority().put(NO_SECURITY, 1);
        config.getCertifierPriority().put(GENERATE, 2);
        config.getCertifierPriority().put(LOAD, 3);
        cleanUpCertificates();
        FileUtils.deleteDirectory(new File(config.getPkiBaseDir()));
        final CompletableFuture<MessageSender.EquipmentState> f = listener.listen();
        controllerProxy.stop();
        try {
            f.get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | CompletionException | ExecutionException e) {
            // assure that server has time to disconnect, fails for test on failed connections
        }
        tagHandler = null;
    }

    @Test
    public void shouldConnectWithoutCertificateIfOthersFail() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        final CompletableFuture<MessageSender.EquipmentState> f = listener.getStateUpdate().get(0);
        controllerProxy.connect(Collections.singleton(uri));
        tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
        assertEquals(MessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertifierPriority().remove(NO_SECURITY);
        config.getCertifierPriority().remove(LOAD);
        final CompletableFuture<MessageSender.EquipmentState> f = trustCertificatesOnServerAndConnect();
        assertEquals(MessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertifierPriority().remove(NO_SECURITY);
        config.getCertifierPriority().remove(GENERATE);
        final CompletableFuture<MessageSender.EquipmentState> f = trustCertificatesOnServerAndConnect();
        assertEquals(MessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void activeKeyChainValidatorShouldFail() {
        config.setTrustAllServers(false);
        config.getCertifierPriority().remove(NO_SECURITY);
        config.getCertifierPriority().remove(GENERATE);
        assertThrows(CommunicationException.class, this::trustCertificatesOnServerAndConnect);
    }

    @Test
    public void serverCertificateInPkiFolderShouldSucceed() throws InterruptedException, OPCUAException, IOException, TimeoutException, ExecutionException {
        config.setTrustAllServers(false);
        config.getCertifierPriority().remove(NO_SECURITY);
        config.getCertifierPriority().remove(GENERATE);

        try {
            trustCertificatesOnServerAndConnect();
        } catch (CommunicationException e) {
            // expected behavior: rejected by the client
        }

        trustCertificatesOnClient();

        final CompletableFuture<MessageSender.EquipmentState> state = listener.listen();
        controllerProxy.connect(Collections.singleton(uri));
        assertEquals(MessageSender.EquipmentState.OK, state.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
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
            controllerProxy.connect(Collections.singleton(uri));
        } catch (CommunicationException e) {
            // expected behavior: rejected by the server
        }
        controllerProxy.stop();

        log.info("Trust certificates server-side and reconnect...");
        trustCertificates();
        final CompletableFuture<MessageSender.EquipmentState> state = listener.listen();

        controllerProxy.connect(Collections.singleton(uri));
        tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
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
