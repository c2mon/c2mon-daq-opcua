package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.*;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;

public class ControllerProxyTest {
    AppConfigProperties config = TestUtils.createDefaultConfig();
    ApplicationContext applicationContext = createMock(ApplicationContext.class);
    Controller proxy;
    TestEndpoint testEndpoint;
    NoFailover noFailover = createNiceMock(NoFailover.class);
    ColdFailover coldFailover = createNiceMock(ColdFailover.class);

    @BeforeEach
    public void setUp() {
        testEndpoint = new TestEndpoint(new TestListeners.TestListener(), new TagSubscriptionMapper());
        proxy = new ControllerProxy(applicationContext, config, testEndpoint);
        expect(applicationContext.getBean(ColdFailover.class)).andReturn(coldFailover).anyTimes();
        expect(applicationContext.getBean(NoFailover.class)).andReturn(noFailover).anyTimes();
    }

    @Test
    public void noConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyTypeNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(noFailover);
    }

    @Test
    public void noConfigShouldQueryServer() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyTypeNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyTypeNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(testEndpoint.getServerRedundancyTypeNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        config.setRedundancyMode(ColdFailover.class.getName());
        mockServerUriCall();
        mockFailoverInitialization(coldFailover, false);
        replay(applicationContext, noFailover, coldFailover, testEndpoint.getServerRedundancyTypeNode());
        proxy.connect(Collections.singleton("test"));
        verify(coldFailover);
    }

    @Test
    public void coldConfigWithUrisNoQueryServer() throws OPCUAException {
        config.setRedundancyMode(ColdFailover.class.getName());
        config.setRedundantServerUris(Arrays.asList("test1", "test2"));
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, noFailover, coldFailover);
        proxy.connect(Arrays.asList("test", "test2"));
        verify(coldFailover);
    }

    private void mockFailoverInitialization(ContreteController mode, boolean expectRedundantAddress) throws OPCUAException {
        if (expectRedundantAddress) {
            mode.initialize(anyObject(), anyString());
        } else {
            mode.initialize(anyObject());
        }
        expectLastCall().atLeastOnce();
    }

    private void mockServerUriCall() {
        expect(testEndpoint.getServerRedundancyTypeNode().getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{"redUri1"}))
                .once();

    }
}
