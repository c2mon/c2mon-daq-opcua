package cern.c2mon.daq.opcua.simengine;

import cern.c2mon.daq.opcua.ConnectionResolver;
import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.SecurityModule;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SimEngineIT {

    private static int PILOT_PORT = 8890;
    private static int SIMENGINE_PORT = 4841;

    private MiloClientWrapper pilotWrapper;
    private MiloClientWrapper simEngineWrapper;
    private static Endpoint pilot;
    private static Endpoint simEngine;
    private static ConnectionResolver resolver;

    SecurityModule p;
    AppConfig config;

    @BeforeAll
    public static void startServers() {
        GenericContainer image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                .withEnv("SIMCONFIG", "sim_BASIC.short.xml")
                .withNetworkMode("host");

        resolver = new ConnectionResolver(image);
        resolver.initialize();
    }

    @AfterAll
    public static void stopServers() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setUp() {
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

        p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config));
        pilotWrapper = new MiloClientWrapperImpl();
        pilotWrapper.initialize(resolver.getURI(PILOT_PORT));
        simEngineWrapper = new MiloClientWrapperImpl();
        simEngineWrapper.initialize(resolver.getURI(SIMENGINE_PORT));

        pilotWrapper.setConfig(config);
        simEngineWrapper.setConfig(config);
        pilotWrapper.setSecurityModule(p);
        simEngineWrapper.setSecurityModule(p);
        pilot = new EndpointImpl(pilotWrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
        simEngine = new EndpointImpl(simEngineWrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
    }

    @AfterEach
    public void tearDown() {
        pilot.reset();
        simEngine.reset();
    }

    @Test
    public void testPilotConnection() {
        pilot.initialize(false);
        Assertions.assertDoesNotThrow(()-> pilot.isConnected());
    }

    @Test
    public void testSimEngineConnection() {
        simEngine.initialize(false);
        Assertions.assertDoesNotThrow(()-> simEngine.isConnected());
    }

    @Test
    public void writeValue() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        simEngine.initialize(false);
        CompletableFuture<Object> writeResponse = ServerTestListener.listenForWriteResponse(simEngine.getPublisher());

        SourceCommandTag commandTag = new SourceCommandTag(0L, "Power");
        OPCHardwareAddressImpl hw = new OPCHardwareAddressImpl("simSY4527.Board00.Chan000.Pw");
        hw.setNamespace(2);
        hw.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.CLASSIC);

        commandTag.setHardwareAddress(hw);
//        SourceCommandTagValue val = new SourceCommandTagValue(commandTag.getId(), commandTag.getName(), 1L, (short) 1, new DataValue(new Variant(1), null, null), "org.eclipse.milo.opcua.stack.core.types.builtin.DataValue");
        //StatusCode write = simEngineWrapper.write(ItemDefinition.toNodeId(hw), new DataValue(new Variant(1), null, null));
        SourceCommandTagValue val = new SourceCommandTagValue();
        val.setValue(1);
        val.setDataType("Integer");
        simEngine.executeCommand(commandTag, val);

        Object o = writeResponse.get(3000, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(StatusCode.GOOD, o);

    }
}
