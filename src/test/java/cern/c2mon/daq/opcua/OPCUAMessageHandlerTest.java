package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static cern.c2mon.daq.opcua.connection.EndpointListener.EquipmentState.CONNECTION_FAILED;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:opcua.properties")
@RunWith(SpringRunner.class)
public class OPCUAMessageHandlerTest extends GenericMessageHandlerTest {

    @Autowired ApplicationContext context;
    @Autowired Controller controller;
    @Autowired EndpointListener endpointListener;
    @Autowired Endpoint miloEndpoint;

    OPCUAMessageHandler handler;
    TestUtils.CommfaultSenderCapture capture;


    @Override
    protected void beforeTest() throws Exception {
        handler = (OPCUAMessageHandler) msgHandler;
        handler.setContext(context);
        ReflectionTestUtils.setField(controller, "failoverProxy", TestUtils.getFailoverProxy(miloEndpoint));
        capture = new TestUtils.CommfaultSenderCapture(messageSender);
    }

    @Override
    protected void afterTest() throws Exception {

    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigButBadEndpointShouldThrowCommunicationError() {
        ReflectionTestUtils.setField(controller, "failoverProxy", TestUtils.getFailoverProxy(new ExceptionTestEndpoint(endpointListener)));
        replay(messageSender);
        assertThrows(CommunicationException.class, () -> handler.connectToDataSource());
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigButBadEndpointShouldSendFAIL() {
        ReflectionTestUtils.setField(controller, "failoverProxy", TestUtils.getFailoverProxy(new ExceptionTestEndpoint(endpointListener)));
        replay(messageSender);
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName() + ":COMM_FAULT",
                CONNECTION_FAILED.message);
    }

    @Test
    @UseConf("address_string_missing_properties.xml")
    public void badAddressStringShouldThrowError() {
        replay(messageSender);
        assertThrows(ConfigurationException.class,
                () -> handler.connectToDataSource(),
                ExceptionContext.URI_SYNTAX.getMessage());
    }

    @Test
    @UseConf("commfault_incorrect.xml")
    public void improperConfigShouldSendFAIL() {
        replay(messageSender);
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName() + ":COMM_FAULT",
                CONNECTION_FAILED.message);
    }

    @Test
    @UseConf("commfault_incorrect.xml")
    public void improperConfigShouldConfigurationError() {
        replay(messageSender);
        assertThrows(ConfigurationException.class, handler::connectToDataSource);
    }
}
