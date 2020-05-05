package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class EdgeIT {
    private ServerTestListener.PulseTestListener listener;

    private static ConnectionResolver resolver;
    private final TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    private final ISourceDataTag tag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    private Controller controller;
    private Endpoint wrapper;

    AppConfig config;

    @BeforeAll
    public static void startServer() {
        resolver = ConnectionResolver.resolveIoTEdgeServer();
        resolver.initialize();
    }

    @AfterAll
    public static void stopServer() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setupEndpoint() throws ConfigurationException {
        config = TestUtils.createDefaultConfig();
        config.setOnDemandCertificationEnabled(false);
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        wrapper = new MiloEndpoint(p);
        listener = new ServerTestListener.PulseTestListener();
        listener.setSourceID(tag.getId());
        controller = new ControllerImpl(wrapper, mapper, listener);
        controller.initialize(resolver.getURI(ConnectionResolver.Ports.IOTEDGE), Collections.singletonList(ServerTagFactory.DipData.createDataTag()));
        log.info("Client ready");
    }

    @AfterEach
    public void cleanUp() {
        controller.stop();
        controller = null;
    }


    @Test
    public void connectToRunningServer() {
        assertDoesNotThrow(()-> controller.isConnected());
    }

    @Test
    public void connectToBadServer() {
        ;
        assertThrows(OPCCommunicationException.class,
                () -> controller.initialize("opc.tcp://somehost/somepath", Collections.singletonList(ServerTagFactory.DipData.createDataTag())));
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() throws ConfigurationException {
        controller.subscribeTags(Collections.singletonList(tag));

        Object o = assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
        assertNotNull(o);
    }

    @Test
    public void subscribingProperTagAndReconnectingShouldKeepResubscribeTags() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        listener.setThreshold(0);
        controller.subscribeTags(Collections.singletonList(tag));


        wrapper.disconnect();
        ((IDataTagChanger) controller).onAddDataTag(ServerTagFactory.DipData.createDataTag(), new ChangeReport());
        listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);

        assertTrue(mapper.isSubscribed(tag));
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () throws ConfigurationException {
        final ISourceDataTag tag = ServerTagFactory.Invalid.createDataTag();
        listener.setSourceID(tag.getId());
        controller.subscribeTags(Collections.singletonList(tag));
        assertDoesNotThrow(() -> listener.getTagInvalid().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithDeadband() throws ConfigurationException {
        var tagWithDeadband = ServerTagFactory.RandomUnsignedInt32.createDataTag(10, (short) DeadbandType.Absolute.getValue(), 0);
        listener.setSourceID(tagWithDeadband.getId());
        controller.subscribeTags(Collections.singletonList(tagWithDeadband));
        assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshProperTag () {
        controller.refreshDataTag(tag);
        assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

}
