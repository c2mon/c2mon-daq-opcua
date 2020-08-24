package cern.c2mon.daq.opcua.opcuamessagehandler;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.taghandling.*;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import org.springframework.context.ApplicationContext;

import static org.easymock.EasyMock.*;

public abstract class OPCUAMessageHandlerTestBase extends GenericMessageHandlerTest {


    protected OPCUAMessageHandler handler;
    protected Controller testController;
    protected ApplicationContext context;
    protected TagSubscriptionManager mapper;
    protected TestEndpoint testEndpoint;
    protected AppConfigProperties appConfigProperties;
    protected IDataTagHandler dataTagHandler;
    protected CommandTagHandler commandTagHandler;
    protected DataTagChanger dataTagChanger;
    protected AliveWriter writer;


    protected void beforeTest(MessageSender sender) {
        context = createMock(ApplicationContext.class);
        mapper = new TagSubscriptionMapper();
        testEndpoint = new TestEndpoint(sender, mapper);
        appConfigProperties = TestUtils.createDefaultConfig();
        testController = TestUtils.getFailoverProxy(testEndpoint, sender);
        dataTagHandler = new DataTagHandler(mapper, sender, testController);
        commandTagHandler = new CommandTagHandler(testController);
        dataTagChanger = new DataTagChanger(dataTagHandler);
        writer = new AliveWriter(testController, sender);

        handler = (OPCUAMessageHandler) msgHandler;
        handler.setContext(context);
    }


    protected void configureContextWithController(Controller controller, MessageSender sender) {
        expect(context.getBean("controller", Controller.class)).andReturn(controller).anyTimes();
        expect(context.getBean(MessageSender.class)).andReturn(sender).anyTimes();
        expect(context.getBean(IDataTagHandler.class)).andReturn(dataTagHandler).anyTimes();
        expect(context.getBean(CommandTagHandler.class)).andReturn(commandTagHandler).anyTimes();
        expect(context.getBean(AppConfigProperties.class)).andReturn(appConfigProperties).anyTimes();
        expect(context.getBean(DataTagChanger.class)).andReturn(dataTagChanger).anyTimes();
        expect(context.getBean(AliveWriter.class)).andReturn(writer).anyTimes();
        replay(context);
    }

}
