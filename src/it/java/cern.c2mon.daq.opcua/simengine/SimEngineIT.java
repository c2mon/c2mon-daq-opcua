package cern.c2mon.daq.opcua.simengine;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.control.Endpoint;
import cern.c2mon.daq.opcua.control.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.WRITE_RESPONSE;

public class SimEngineIT {
    private static Endpoint pilot;
    private static Endpoint simEngine;
    private static ConnectionResolver resolver;

    SecurityModule p;
    @BeforeAll
    public static void startServers() {
        resolver = ConnectionResolver.resolveVenusServers("sim_BASIC.short.xml");
        resolver.initialize();
    }

    @AfterAll
    public static void stopServers() {
        resolver.close();
        resolver = null;
    }

    @BeforeEach
    public void setUp() {
        AppConfig config = TestUtils.createDefaultConfig();
        p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());

        pilot = new EndpointImpl(new MiloClientWrapper(p), new TagSubscriptionMapperImpl(), new EventPublisher());
        simEngine = new EndpointImpl(new MiloClientWrapper(p), new TagSubscriptionMapperImpl(), new EventPublisher());

        pilot.initialize(resolver.getURI(ConnectionResolver.Ports.PILOT));
        simEngine.initialize(resolver.getURI(ConnectionResolver.Ports.SIMENGINE));
    }

    @AfterEach
    public void tearDown() {
        pilot.reset();
        simEngine.reset();
    }

    @Test
    public void writeValue() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        simEngine.connect(false);
        CompletableFuture<Object> writeResponse = ServerTestListener.createListenerAndReturnFutures(simEngine.getPublisher()).get(WRITE_RESPONSE);

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
