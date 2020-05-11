package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ControlDelegate;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@TestPropertySource(locations = "classpath:opcua.properties")
public class EdgeIT {

    private static ConnectionResolver resolver;
    private final ISourceDataTag tag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
    private final ServerTestListener.PulseTestListener listener = new ServerTestListener.PulseTestListener();

    @Autowired
    TagSubscriptionMapper mapper;

    @Autowired
    ControlDelegate delegate;

    @Autowired
    Controller controller;

    @Autowired
    Endpoint endpoint;

    @Autowired
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
        listener.setSourceID(tag.getId());
        ReflectionTestUtils.setField(controller, "endpointListener", listener);
        delegate.initialize(resolver.getURI(ConnectionResolver.Ports.IOTEDGE), Collections.singletonList(ServerTagFactory.DipData.createDataTag()));
        log.info("Client ready");
    }

    @AfterEach
    public void cleanUp() {
        delegate.stop();
    }


    @Test
    public void connectToRunningServer() {
        assertDoesNotThrow(()-> controller.isConnected());
    }

    @Test
    public void connectToBadServer() {
        assertThrows(OPCCriticalException.class,
                () -> delegate.initialize("opc.tcp://somehost/somepath", Collections.singletonList(ServerTagFactory.DipData.createDataTag())));
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() throws ConfigurationException, CommunicationException {
        controller.subscribeTags(Collections.singletonList(tag));

        Object o = assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
        assertNotNull(o);
    }

    @Test
    public void subscribingProperTagAndReconnectingShouldKeepResubscribeTags() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException, CommunicationException {
        listener.setThreshold(0);
        controller.subscribeTags(Collections.singletonList(tag));
        endpoint.disconnect();
        delegate.refreshAllDataTags();
        listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);

        assertTrue(mapper.isSubscribed(tag));
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () throws ConfigurationException, CommunicationException {
        final ISourceDataTag tag = ServerTagFactory.Invalid.createDataTag();
        listener.setSourceID(tag.getId());
        controller.subscribeTags(Collections.singletonList(tag));
        assertDoesNotThrow(() -> listener.getTagInvalid().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithDeadband() throws ConfigurationException, CommunicationException {
        var tagWithDeadband = ServerTagFactory.RandomUnsignedInt32.createDataTag(10, (short) DeadbandType.Absolute.getValue(), 0);
        listener.setSourceID(tagWithDeadband.getId());
        controller.subscribeTags(Collections.singletonList(tagWithDeadband));
        assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshProperTag () throws CommunicationException {
        controller.refreshDataTag(tag);
        assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

}
