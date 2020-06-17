package cern.c2mon.daq.opcua.simengine;

import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.control.AliveWriter;
import cern.c2mon.daq.opcua.control.CommandRunner;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.TagChanger;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.testutils.ConnectionResolver;
import cern.c2mon.daq.opcua.testutils.TestListeners;
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
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(locations = "classpath:opcua.properties")
public class SimEngineMessageHandlerIT extends GenericMessageHandlerTest {

    private static ConnectionResolver.Venus resolver;
    private OPCUAMessageHandler handler;
    private SourceCommandTagValue value;
    private final TestListeners.Pulse listener = new TestListeners.Pulse();

    @Autowired
    Controller controller;

    @Autowired
    CommandRunner commandRunner;

    @Autowired
    TagChanger tagChanger;

    @Autowired
    AliveWriter aliveWriter;

    @Autowired
    FailoverProxy failoverProxy;

    private static final long DATAID_VMON = 1L;
    private static final long DATAID_PW = 2L;
    private static final long CMDID_PW = 10L;
    private static final long CMDID_V0SET = 20L;


    @BeforeClass
    public static void startServer() {
        // TODO: don't extend MessageHandler but use spring boot for DI, migrate all tests to junit 5
        resolver = new ConnectionResolver.Venus("sim_BASIC.short.xml");
    }

    @AfterClass
    public static void stopServer() {
        resolver.close();
    }


    @Override
    protected void beforeTest () throws Exception {
        log.info("############### SET UP ##############");
        handler = (OPCUAMessageHandler) msgHandler;
        value = new SourceCommandTagValue();
        value.setDataType("java.lang.Integer");
        ReflectionTestUtils.setField(handler, "controller", controller);
        ReflectionTestUtils.setField(handler, "aliveWriter", aliveWriter);
        ReflectionTestUtils.setField(handler, "tagChanger", tagChanger);
        ReflectionTestUtils.setField(handler, "commandRunner", commandRunner);
        ReflectionTestUtils.setField(handler, "listener", listener);
        ReflectionTestUtils.setField(controller, "endpointListener", listener);
        ReflectionTestUtils.setField(failoverProxy, "endpointListener", listener);
        mapEquipmentAddress();
        log.info("############### TEST ##############");
    }

    // Work-around for non-fixed ports in testcontainers, since GenericMessageHandlerTest required ports to be included in the configuration
    private void mapEquipmentAddress() {
        final String hostRegex = ":(.[^/][^;]*)";
        final String equipmentAddress = equipmentConfiguration.getEquipmentAddress();

        final Matcher matcher = Pattern.compile(hostRegex).matcher(equipmentAddress);
        if (matcher.find()) {
            final String port = matcher.group(1);
            final String mappedAddress = equipmentAddress.replace(port + "", resolver.getMappedPort(Integer.parseInt(port)) + "");
            ReflectionTestUtils.setField(equipmentConfiguration, "equipmentAddress", mappedAddress);
        }
    }

    @After
    public void cleanUp() throws Exception {
        log.info("############### CLEAN UP ##############");
        // must be called before the handler disconnect in GenericMessageHandlerTest's afterTest method
        log.info("Cleaning up... ");
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
        assertThrows(ConfigurationException.class, handler::connectToDataSource);
    }

    @Test
    @UseConf("simengine_power.xml")
    public void write1SetsValueTo1() throws EqIOException, EqCommandTagException, InterruptedException, ExecutionException, TimeoutException {
        listener.setSourceID(DATAID_PW);
        listener.setThreshold(1);
        handler.connectToDataSource();

        setIDTo(CMDID_PW, 1);

        // Assert power is set to 1
        final ValueUpdate valueUpdate = listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        assertEquals(1, valueUpdate.getValue());


    }


    @Test
    @UseConf("simengine_power.xml")
    public void testPowerOnAndSetV0ShouldNotifyVMon() throws EqIOException, EqCommandTagException {
        listener.setSourceID(DATAID_VMON);
        listener.setThreshold(10);
        handler.connectToDataSource();
        setIDTo(CMDID_PW, 1);
        setIDTo(CMDID_V0SET, 10);

        // Assert monitored voltage close to threshold within reasonable time
        assertDoesNotThrow(() -> listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }


    @Test
    @UseConf("simengine_power_pulse.xml")
    public void testPowerOnAndOff() throws EqIOException, ExecutionException, InterruptedException, EqCommandTagException, TimeoutException {
        listener.setSourceID(DATAID_PW);
        listener.setThreshold(1);
        handler.connectToDataSource();
        setIDTo(CMDID_PW, 1);

        final ValueUpdate pwOnUpdate = listener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        final ValueUpdate pwOffUpdate = listener.getPulseTagUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        final long timeDiff = pwOffUpdate.getSourceTimestamp() - pwOnUpdate.getSourceTimestamp();

        assertEquals(1, pwOnUpdate.getValue());
        assertEquals(0, pwOffUpdate.getValue());

        // A pulse length of 1 second, plus a margin for connection lag
        assertTrue(timeDiff >= 0 && timeDiff < 4000);
    }

    private void setIDTo(long id, float val) throws EqCommandTagException {
        value.setValue(val);
        value.setId(id);
        handler.runCommand(value);
    }
}
