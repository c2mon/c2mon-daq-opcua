package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.AliveWriter;
import cern.c2mon.daq.opcua.control.ControlDelegate;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.MiloMocker;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.CONNECTION_FAILED;
import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.OK;
import static cern.c2mon.daq.opcua.exceptions.ConfigurationException.Cause.ADDRESS_MISSING_PROPERTIES;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:opcua.properties")
@RunWith(SpringRunner.class)
public class OPCUAMessageHandlerTest extends GenericMessageHandlerTest {

    @Autowired
    ControlDelegate delegate;

    @Autowired
    Controller controller;

    @Autowired
    AliveWriter aliveWriter;

    @Autowired
    @Qualifier("endpointListener")
    EndpointListener listener;

    @Autowired
    TagSubscriptionMapper mapper;

    @Autowired
    @Qualifier("miloEndpoint")
    Endpoint miloEndpoint;

    @Autowired
    @Qualifier("testEndpoint")
    Endpoint testEndpoint;

    OPCUAMessageHandler handler;
    TestUtils.CommfaultSenderCapture capture;


    @Override
    protected void beforeTest () throws Exception {
        // must be injected manually to work with test framework
        handler = (OPCUAMessageHandler) msgHandler;
        handler.setDelegate(delegate);
        handler.setAliveWriter(aliveWriter);
        handler.setListener(listener);
        capture = new TestUtils.CommfaultSenderCapture(messageSender);
    }

    @Override
    protected void afterTest () throws Exception {
        controller.setEndpoint(miloEndpoint);
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigShouldSendOKCommfault () throws EqIOException {
        controller.setEndpoint(testEndpoint);
        MiloMocker mocker = new MiloMocker(testEndpoint, mapper);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, equipmentConfiguration.getSourceDataTags().values());
        replay(messageSender, ((TestEndpoint) testEndpoint).getMonitoredItem());

        handler.connectToDataSource();

        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName()+":COMM_FAULT",
                OK.message);
    }

    @Test
    @UseConf("bad_address_string.xml")
    public void badAddressStringShouldThrowError () {
        replay(messageSender);
        assertThrows(ConfigurationException.class,
                () -> handler.connectToDataSource(),
                ADDRESS_MISSING_PROPERTIES.message);
    }

    @Test
    @UseConf("commfault_incorrect.xml")
    public void improperConfigShouldSendIncorrectCommfault () {
        replay(messageSender);
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
        }

        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName()+":COMM_FAULT",
                CONNECTION_FAILED.message);
    }

    @Test
    @UseConf("commfault_incorrect.xml")
    public void improperConfigShouldThrowError () {
        replay(messageSender);
        assertThrows(OPCCriticalException.class, handler::connectToDataSource);
    }
}
