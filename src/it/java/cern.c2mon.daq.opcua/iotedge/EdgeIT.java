package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.security.SecurityModule;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.Assert;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.TAG_INVALID;
import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.TAG_UPDATE;

@Slf4j
public class EdgeIT {

    private static int TIMEOUT = 6000;
    private Map<ServerTestListener.Target, CompletableFuture<Object>> future;

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
        future = ServerTestListener.createListenerAndReturnFutures(publisher, 0.0f);
        config = TestUtils.createDefaultConfig();
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        endpoint = new EndpointImpl(new MiloClientWrapperImpl(p), mapper, publisher);
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
        Assertions.assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectToBadServer() {
        endpoint.initialize("opc.tcp://somehost/somepath");
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.connect(false));
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() {
        endpoint.subscribeTag(ServerTagFactory.RandomUnsignedInt32.createDataTag());

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TAG_UPDATE).get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () throws ExecutionException, InterruptedException, TimeoutException {
        endpoint.subscribeTag(ServerTagFactory.Invalid.createDataTag());
        Assertions.assertDoesNotThrow(() -> future.get(TAG_INVALID).get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeAndSetDeadband() {
        float valueDeadband = 50;
        CompletableFuture<Object> f = ServerTestListener.listenForTagUpdate(publisher, valueDeadband);
        ISourceDataTag dataTag = ServerTagFactory.DipData.createDataTag(valueDeadband, (short) DeadbandType.Absolute.getValue(), 0);

        endpoint.subscribeTag(dataTag);

        Object o = Assertions.assertDoesNotThrow(() -> f.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void refreshProperTag () {
        endpoint.refreshDataTags(Collections.singletonList(ServerTagFactory.RandomUnsignedInt32.createDataTag()));

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TAG_UPDATE).get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

}
