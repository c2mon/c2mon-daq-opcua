package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.ConnectionResolver;
import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.security.SecurityModule;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class EdgeIT {

    private static int PORT = 50000;
    private static int TIMEOUT = 6000;
    private CompletableFuture<Object> future;

    private Endpoint endpoint;
    private TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    private MiloClientWrapper wrapper;
    private EventPublisher publisher = new EventPublisher();
    private static ConnectionResolver resolver;

    SecurityModule p;
    AppConfig config;

    @BeforeAll
    public static void startServer() {
        GenericContainer image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        resolver = new ConnectionResolver(image);
        resolver.initialize();
    }

    @AfterAll
    public static void stopServer() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setupEndpoint() {
        future = listenForServerResponse();
        wrapper = new MiloClientWrapperImpl();
        wrapper.initialize(resolver.getURI(PORT));
        config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .enableInsecureCommunication(true)
                .enableOnDemandCertification(true)
                .build();
        p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        wrapper.setSecurityModule(p);
        wrapper.setConfig(config);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
        endpoint.initialize(false);
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
        wrapper = new MiloClientWrapperImpl();
        wrapper.initialize("opc.tcp://somehost/somepath");
        wrapper.setSecurityModule(p);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize(false));
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() {
        CompletableFuture<Object> future = listenForServerResponse();

        endpoint.subscribeTag(ServerTagFactory.RandomUnsignedInt32.createDataTag());

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () throws ExecutionException, InterruptedException {
        endpoint.subscribeTag(ServerTagFactory.Invalid.createDataTag());

        SourceDataTagQuality response = (SourceDataTagQuality) future.get();
        Assertions.assertEquals(SourceDataTagQualityCode.INCORRECT_NATIVE_ADDRESS, response.getQualityCode());
    }

    @Test
    public void subscribeAndSetDeadband() {
        float valueDeadband = 50;
        CompletableFuture<Object> future = ServerTestListener.listenForTagResponse(publisher, valueDeadband);
        ISourceDataTag dataTag = ServerTagFactory.DipData.createDataTag(valueDeadband, (short) DeadbandType.Absolute.getValue(), 0);

        endpoint.subscribeTag(dataTag);

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void refreshProperTag () {
        CompletableFuture<Object> future = listenForServerResponse();

        endpoint.refreshDataTags(Collections.singletonList(ServerTagFactory.RandomUnsignedInt32.createDataTag()));

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    private CompletableFuture<Object> listenForServerResponse() {
        return ServerTestListener.listenForTagResponse(publisher, 0.0f);
    }

}
