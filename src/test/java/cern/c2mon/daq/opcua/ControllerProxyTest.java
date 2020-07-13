package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;

public class ControllerProxyTest {
    AppConfigProperties config = TestUtils.createDefaultConfig();
    ApplicationContext applicationContext = createMock(ApplicationContext.class);
    ControllerProxy proxy;
    TestEndpoint testEndpoint;
    Controller singleServerController = createNiceMock(Controller.class);
    ColdFailoverDecorator coldFailover = createNiceMock(ColdFailoverDecorator.class);

    @BeforeEach
    public void setUp() {
        final TestListeners.TestListener listener = new TestListeners.TestListener(new TagSubscriptionManager());
        testEndpoint = new TestEndpoint(listener);
        proxy = new ControllerProxyImpl(applicationContext, config, listener, singleServerController);
        expect(coldFailover.currentEndpoint()).andReturn(testEndpoint).anyTimes();
        expect(singleServerController.currentEndpoint()).andReturn(testEndpoint).anyTimes();
    }

    @Test
    public void noConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(singleServerController);
        replay(testEndpoint.getServerRedundancyTypeNode(), singleServerController);
        proxy.connect("test");
        verify(singleServerController);
    }

    @Test
    public void noConfigShouldQueryServer() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(singleServerController);
        replay(testEndpoint.getServerRedundancyTypeNode(), singleServerController);
        proxy.connect("test");
        verify(testEndpoint.getServerRedundancyTypeNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        config.setRedundancyMode(ColdFailoverDecorator.class.getName());
        mockServerUriCall();
        expect(applicationContext.getBean(EasyMock.<Class>anyObject())).andReturn(coldFailover).anyTimes();
        mockFailoverInitialization(coldFailover);
        replay(applicationContext, singleServerController, coldFailover,testEndpoint.getServerRedundancyTypeNode());
        proxy.connect("test");
        verify(coldFailover);
    }

    @Test
    public void coldConfigWithUrisNoQueryServer() throws OPCUAException {
        config.setRedundancyMode(ColdFailoverDecorator.class.getName());
        config.setRedundantServerUris(Arrays.asList("test1", "test2"));
        expect(applicationContext.getBean(EasyMock.<Class>anyObject())).andReturn(coldFailover).anyTimes();
        mockFailoverInitialization(coldFailover);
        replay(applicationContext, singleServerController, coldFailover);
        proxy.connect("test");
        verify(coldFailover);
    }

    private void mockFailoverInitialization(Controller mode) throws OPCUAException {
        mode.initializeMonitoring(anyObject());
        expectLastCall().atLeastOnce();
    }

    private void mockServerUriCall() {
        expect(testEndpoint.getServerRedundancyTypeNode().getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{"redUri1"}))
                .once();

    }
}
