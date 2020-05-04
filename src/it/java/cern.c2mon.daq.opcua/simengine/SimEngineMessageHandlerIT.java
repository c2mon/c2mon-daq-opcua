package cern.c2mon.daq.opcua.simengine;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.AliveWriter;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerImpl;
import cern.c2mon.daq.opcua.control.EndpointImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.OPCUAMessageHandlerTest.CommfaultSenderCapture;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
public class SimEngineMessageHandlerIT extends GenericMessageHandlerTest {

    private static ConnectionResolver resolver;
    OPCUAMessageHandler handler;
    SourceCommandTagValue value;
    CommfaultSenderCapture capture;

    private static final long DATAID_VMON = 1L;
    private static final long DATAID_PW = 2L;
    private static final long CMDID_PW = 10L;
    private static final long CMDID_V0SET = 20L;

    @BeforeClass
    public static void startServer() {
        // TODO: don't extend MessageHandler but use spring boot for DI, migrate all tests to junit 5
        resolver = ConnectionResolver.resolveVenusServers("sim_BASIC.short.xml");
        resolver.initialize();
    }

    @AfterClass
    public static void stopServer() {
        resolver.close();
    }

    @Override
    protected void beforeTest () throws Exception {
        handler = (OPCUAMessageHandler) msgHandler;
        capture = new CommfaultSenderCapture(messageSender);

        value = new SourceCommandTagValue();

        value.setDataType("java.lang.Integer");

        AppConfig config = TestUtils.createDefaultConfig();
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        EndpointImpl endpoint = new EndpointImpl(new MiloClientWrapper(p), new TagSubscriptionMapperImpl(), new EventPublisher());
        Controller controller = new ControllerImpl(endpoint, new AliveWriter(endpoint));

        handler.setController(controller);
    }

    @After
    public void cleanUp() throws Exception {
        //must be called before the handler disconnect in GenericMessageHandlerTest's afterTest method
        value.setValue(0);
        for(long id : handler.getEquipmentConfiguration().getSourceCommandTags().keySet()) {
            value.setId(id);
            handler.runCommand(value);
        }

        super.cleanUp();
    }

    @Override
    protected void afterTest () throws Exception {
    }

    @Test
    @UseConf("empty_datatags.xml")
    public void emptyDatatagsShouldThrowError () {
        assertThrows(ConfigurationException.class, () -> handler.connectToDataSource());
    }

    @Test
    @UseConf("simengine_power.xml")
    public void write1SetsValueTo1() throws EqIOException, EqCommandTagException, InterruptedException, ExecutionException, TimeoutException {
        final ServerTestListener.PulseTestListener listener = new ServerTestListener.PulseTestListener(DATAID_PW);
        listener.setThreshold(1);
        handler.setListener(listener);
        handler.connectToDataSource();

        setIDTo(CMDID_PW, 1);

        // Assert power is set to 1
        final ValueUpdate valueUpdate = listener.getTagValUpdate().get(3000, TimeUnit.MILLISECONDS);
        assertEquals(1, valueUpdate.getValue());

    }


    @Test
    @UseConf("simengine_power.xml")
    public void testPowerOnAndSetV0ShouldNotifyVMon() throws EqIOException, EqCommandTagException {
        final ServerTestListener.PulseTestListener listener = new ServerTestListener.PulseTestListener(DATAID_VMON);
        listener.setThreshold(10);
        handler.setListener(listener);

        handler.connectToDataSource();
        setIDTo(CMDID_PW, 1);
        setIDTo(CMDID_V0SET, 10);

        // Assert monitored voltage close to threshold within reasonable time
        assertDoesNotThrow(() -> listener.getTagValUpdate().get(6000, TimeUnit.MILLISECONDS));
    }


    @Test
    @UseConf("simengine_power_pulse.xml")
    public void testPowerOnAndOff() throws EqIOException, ExecutionException, InterruptedException, EqCommandTagException, TimeoutException {
        final ServerTestListener.PulseTestListener listener = new ServerTestListener.PulseTestListener(DATAID_PW);
        listener.setThreshold(1);
        handler.setListener(listener);
        handler.connectToDataSource();
        setIDTo(CMDID_PW, 1);

        final ValueUpdate pwOnUpdate = listener.getTagValUpdate().get(3000, TimeUnit.MILLISECONDS);
        final ValueUpdate pwOffUpdate = listener.getPulseTagUpdate().get(3000, TimeUnit.MILLISECONDS);
        final long timeDiff = pwOffUpdate.getSourceTimestamp() - pwOnUpdate.getSourceTimestamp();

        assertEquals(1, pwOnUpdate.getValue());
        assertEquals(0, pwOffUpdate.getValue());

        // A pulse length of 1 second, plus a margin for connection lag
        assertTrue(timeDiff > 0 && timeDiff < 3000);


    }

    private void setIDTo(long id, float val) throws EqCommandTagException {
        value.setValue(val);
        value.setId(id);
        handler.runCommand(value);
    }
}
