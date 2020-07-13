package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
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
    NoFailover noFailover = createNiceMock(NoFailover.class);
    ColdFailover coldFailover = createNiceMock(ColdFailover.class);

    @BeforeEach
    public void setUp() {
        final TestListeners.TestListener listener = new TestListeners.TestListener(new TagSubscriptionManager());
        testEndpoint = new TestEndpoint(listener);
        proxy = new ControllerProxyImpl(applicationContext, config, listener, testEndpoint);
        expect(applicationContext.getBean(ColdFailover.class)).andReturn(coldFailover).anyTimes();
        expect(applicationContext.getBean(NoFailover.class)).andReturn(noFailover).anyTimes();
    }

    @Test
    public void noConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover);
        replay(applicationContext, testEndpoint.getServerRedundancyTypeNode(), noFailover);
        proxy.connect("test");
        verify(noFailover);
    }

    @Test
    public void noConfigShouldQueryServer() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover);
        replay(applicationContext, testEndpoint.getServerRedundancyTypeNode(), noFailover);
        proxy.connect("test");
        verify(testEndpoint.getServerRedundancyTypeNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        config.setRedundancyMode(ColdFailover.class.getName());
        mockServerUriCall();
        mockFailoverInitialization(coldFailover);
        replay(applicationContext, noFailover, coldFailover,testEndpoint.getServerRedundancyTypeNode());
        proxy.connect("test");
        verify(coldFailover);
    }

    @Test
    public void coldConfigWithUrisNoQueryServer() throws OPCUAException {
        config.setRedundancyMode(ColdFailover.class.getName());
        config.setRedundantServerUris(Arrays.asList("test1", "test2"));
        mockFailoverInitialization(coldFailover);
        replay(applicationContext, noFailover, coldFailover);
        proxy.connect("test");
        verify(coldFailover);
    }

    private void mockFailoverInitialization(Controller mode) throws OPCUAException {
        mode.initialize(anyObject(), anyObject());
        expectLastCall().atLeastOnce();
    }

    private void mockServerUriCall() {
        expect(testEndpoint.getServerRedundancyTypeNode().getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{"redUri1"}))
                .once();

    }
}
