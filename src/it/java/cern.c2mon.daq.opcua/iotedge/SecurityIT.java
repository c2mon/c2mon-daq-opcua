package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.IControllerProxy;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
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
    @Autowired
    IControllerProxy controllerProxy;
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
        config.getCertificationPriority().put("none", 1);
        config.getCertificationPriority().put("generate", 2);
        config.getCertificationPriority().put("load", 3);
        cleanUpCertificates();
        FileUtils.deleteDirectory(new File(config.getPkiBaseDir()));
        final CompletableFuture<IMessageSender.EquipmentState> f = listener.listen();
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
        final CompletableFuture<IMessageSender.EquipmentState> f = listener.getStateUpdate();
        controllerProxy.connect(Collections.singleton(uri));
        tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.DipData.createDataTag()));
        assertEquals(IMessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedSelfSignedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("load");
        final CompletableFuture<IMessageSender.EquipmentState> f = trustCertificatesOnServerAndConnect();
        assertEquals(IMessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void trustedLoadedCertificateShouldAllowConnection() throws IOException, InterruptedException, OPCUAException, TimeoutException, ExecutionException {
        config.getCertificationPriority().remove("none");
        config.getCertificationPriority().remove("generate");
        final CompletableFuture<IMessageSender.EquipmentState> f = trustCertificatesOnServerAndConnect();
        assertEquals(IMessageSender.EquipmentState.OK, f.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
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

        final CompletableFuture<IMessageSender.EquipmentState> state = listener.listen();
        controllerProxy.connect(Collections.singleton(uri));
        assertEquals(IMessageSender.EquipmentState.OK, state.get(TestUtils.TIMEOUT_IT*2, TimeUnit.MILLISECONDS));
    }

    private void trustCertificatesOnClient() throws IOException {
        final File[] files = new File(config.getPkiBaseDir() + "/rejected").listFiles();
        for (File file : files) {
            log.info("Trusting Certificate {}. ", file.getName());
            Files.move(file, new File(config.getPkiBaseDir() + "/trusted/certs/" + file.getName()));
        }
    }

    private CompletableFuture<IMessageSender.EquipmentState> trustCertificatesOnServerAndConnect() throws IOException, InterruptedException, OPCUAException {
        log.info("Initial connection attempt...");
        try {
            controllerProxy.connect(Collections.singleton(uri));
        } catch (CommunicationException e) {
            // expected behavior: rejected by the server
        }
        controllerProxy.stop();

        log.info("Trust certificates server-side and reconnect...");
        trustCertificates();
        final CompletableFuture<IMessageSender.EquipmentState> state = listener.listen();

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
