package cern.c2mon.daq.opcua.opcuamessagehandler;

import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.OPCUAMessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.UriModifier;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.scope.EquipmentScope;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.TestControllerProxy;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_FAILED;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
public class OPCUAMessageHandlerCommfaultTest extends OPCUAMessageHandlerTestBase {

    OPCUAMessageSender sender;
    TestUtils.CommfaultSenderCapture capture;

    @Override
    protected void beforeTest() throws Exception {

        sender = new OPCUAMessageSender();
        super.beforeTest(sender);
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
        configureContextWithAutowiredEndpoint();
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


    private void configureContextWithAutowiredEndpoint() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(MiloEndpoint.class, SecurityModule.class, CertificateGenerator.class, CertificateLoader.class,
                    NoSecurityCertifier.class, TagSubscriptionMapper.class, RetryDelegate.class, AppConfigProperties.class,
                    UriModifier.class, OPCUAMessageSender.class);
            final EquipmentScope s = new EquipmentScope();
            ctx.refresh();
            ((ConfigurableBeanFactory) ctx.getAutowireCapableBeanFactory()).registerScope("equipment", s);
            final Endpoint miloEndpoint = ctx.getBean(Endpoint.class);
            final TestControllerProxy proxy = TestUtils.getFailoverProxy(miloEndpoint, sender);
            configureContextWithController(proxy, sender);
        }
    }
}

