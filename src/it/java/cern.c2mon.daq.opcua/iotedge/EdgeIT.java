package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.Endpoint;
import cern.c2mon.daq.opcua.control.EndpointImpl;
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

    private static int TIMEOUT = 6000;
    private ServerTestListener.TestListener future;

    private Endpoint endpoint;
    private final TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    private final EventPublisher publisher = new EventPublisher();
    private static ConnectionResolver resolver;

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
    public void setupEndpoint() {
        future = ServerTestListener.subscribeAndReturnListener(publisher);
        config = TestUtils.createDefaultConfig();
        config.setEnableOnDemandCertification(false);
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        endpoint = new EndpointImpl(new MiloClientWrapper(p), mapper, publisher);
        endpoint.initialize(resolver.getURI(ConnectionResolver.Ports.IOTEDGE));
        endpoint.connect(false);
        log.info("Client ready");
    }

    @AfterEach
    public void cleanUp() {
        endpoint.reset();
        endpoint = null;
    }


    @Test
    public void connectToRunningServer() {
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectToBadServer() {
        endpoint.initialize("opc.tcp://somehost/somepath");
        assertThrows(OPCCommunicationException.class, () -> endpoint.connect(false));
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() {
        endpoint.subscribeTag(ServerTagFactory.RandomUnsignedInt32.createDataTag());

        Object o = assertDoesNotThrow(() -> future.getTagUpdate().get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () {
        endpoint.subscribeTag(ServerTagFactory.Invalid.createDataTag());
        assertDoesNotThrow(() -> future.getTagInvalid().get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithDeadband() throws InterruptedException, ExecutionException, TimeoutException {
        float valueDeadband = 10;
        final ISourceDataTag tag = ServerTagFactory.DipData.createDataTag(valueDeadband, (short) DeadbandType.Absolute.getValue(), 0);
        endpoint.subscribeTag(tag);
        assertEquals(tag.getId(), future.getTagUpdate().get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshProperTag () throws InterruptedException, ExecutionException, TimeoutException {
        final ISourceDataTag tag = ServerTagFactory.RandomUnsignedInt32.createDataTag();
        endpoint.refreshDataTags(Collections.singletonList(tag));
        assertEquals(tag.getId(), future.getTagUpdate().get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

}
