package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
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

public class FailoverProxyTest {
    AppConfigProperties config = TestUtils.createDefaultConfig();
    ApplicationContext applicationContext = createMock(ApplicationContext.class);
    FailoverProxy proxy;
    TestEndpoint testEndpoint;
    NoFailover noFailover = createMock(NoFailover.class);
    ColdFailover coldFailover = createMock(ColdFailover.class);

    @BeforeEach
    public void setUp() {
        final TestListeners.TestListener listener = new TestListeners.TestListener(new TagSubscriptionMapperImpl());
        testEndpoint = new TestEndpoint(listener);
        proxy = new FailoverProxyImpl(applicationContext, config, listener, noFailover);
        expect(coldFailover.currentEndpoint()).andReturn(testEndpoint).anyTimes();
        expect(noFailover.currentEndpoint()).andReturn(testEndpoint).anyTimes();
    }

    @Test
    public void noConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover);
        replay(testEndpoint.getServerRedundancyTypeNode(), noFailover);
        proxy.connect("test");
        verify(noFailover);
    }

    @Test
    public void noConfigShouldQueryServer() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover);
        replay(testEndpoint.getServerRedundancyTypeNode(), noFailover);
        proxy.connect("test");
        verify(testEndpoint.getServerRedundancyTypeNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        config.setRedundancyMode(ColdFailover.class.getName());
        mockServerUriCall();
        expect(applicationContext.getBean(EasyMock.<Class>anyObject())).andReturn(coldFailover).anyTimes();
        mockFailoverInitialization(coldFailover);
        replay(applicationContext, noFailover, coldFailover,testEndpoint.getServerRedundancyTypeNode());
        proxy.connect("test");
        verify(coldFailover);
    }

    @Test
    public void coldConfigWithUrisNoQueryServer() throws OPCUAException {
        config.setRedundancyMode(ColdFailover.class.getName());
        config.setRedundantServerUris(Arrays.asList("test1", "test2"));
        expect(applicationContext.getBean(EasyMock.<Class>anyObject())).andReturn(coldFailover).anyTimes();
        mockFailoverInitialization(coldFailover);
        replay(applicationContext, noFailover, coldFailover);
        proxy.connect("test");
        verify(coldFailover);
    }

    private void mockFailoverInitialization(Controller mode) throws OPCUAException {
        mode.initializeMonitoring(anyString(), anyObject(), anyObject());
        expectLastCall().atLeastOnce();
    }

    private void mockServerUriCall() {
        expect(testEndpoint.getServerRedundancyTypeNode().getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{"redUri1"}))
                .once();

    }
}
