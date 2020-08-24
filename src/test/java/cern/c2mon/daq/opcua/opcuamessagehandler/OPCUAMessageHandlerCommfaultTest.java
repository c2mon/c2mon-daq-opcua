package cern.c2mon.daq.opcua.opcuamessagehandler;

import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.OPCUAMessageSender;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.testutils.TestControllerProxy;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_FAILED;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@RunWith(SpringRunner.class)
public class OPCUAMessageHandlerCommfaultTest extends OPCUAMessageHandlerTestBase {

    @Autowired MiloEndpoint miloEndpoint;
    OPCUAMessageSender sender;
    TestControllerProxy miloController;
    TestUtils.CommfaultSenderCapture capture;

    @Override
    protected void beforeTest() throws Exception {
        sender = new OPCUAMessageSender();
        super.beforeTest(sender);
        miloController = TestUtils.getFailoverProxy(miloEndpoint, sender);
        testEndpoint.setThrowExceptions(true);
        testEndpoint.setDelay(100);
        capture = new TestUtils.CommfaultSenderCapture(this.messageSender);
    }

    @Override
    protected void afterTest() throws Exception {
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigButBadEndpointShouldThrowCommunicationError() {
        configureContextWithController(testController, sender);
        replay(messageSender);
        assertThrows(CommunicationException.class, handler::connectToDataSource);
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigButBadEndpointShouldSendFAIL() {
        configureContextWithController(testController, sender);
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
    @UseConf("bad_address_string.xml")
    public void badAddressStringShouldThrowError() {
        configureContextWithController(testController, sender);
        replay(messageSender);
        assertThrows(ConfigurationException.class,
                () -> handler.connectToDataSource(),
                ExceptionContext.URI_SYNTAX.getMessage());
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void cannotEstablishInitialConnectionShouldSendFAIL() {
        configureContextWithController(miloController, sender);
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
    @UseConf("commfault_ok.xml")
    public void cannotEstablishInitialConnectionShouldConfigurationError() {
        configureContextWithController(miloController, sender);
        replay(messageSender);
        assertThrows(ConfigurationException.class, handler::connectToDataSource);
    }
}
