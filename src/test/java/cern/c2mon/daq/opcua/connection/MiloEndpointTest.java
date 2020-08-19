package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import lombok.Getter;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MiloEndpointTest {

    Endpoint endpoint;
    OpcUaClient mockClient = createNiceMock(OpcUaClient.class);
    RetryDelegate retryDelegate = createNiceMock(RetryDelegate.class);
    SecurityModule securityModule = createNiceMock(SecurityModule.class);
    OpcUaSubscriptionManager subscriptionManager = createNiceMock(OpcUaSubscriptionManager.class);
    TestListeners.TestListener listener = new TestListeners.TestListener();

    @BeforeEach
    public void setUp() {
        AppConfigProperties config = AppConfigProperties.builder().maxRetryAttempts(3).requestTimeout(300).retryDelay(1000).build();
        endpoint = new MiloEndpoint(securityModule, retryDelegate, null, null, config.exponentialDelayTemplate());
        expect(mockClient.getSubscriptionManager()).andReturn(subscriptionManager).anyTimes();
        replay(mockClient);
    }

    @Test
    public void exceptionInDiscoveryShouldBeEscalated() throws OPCUAException {
        expect(retryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andThrow(new CommunicationException(ExceptionContext.CONNECT))
                .anyTimes();
        replay(retryDelegate);
        assertThrows(OPCUAException.class, () -> endpoint.initialize("test"));
    }


    @Test
    public void exceptionInSecurityModuleShouldBeEscalated() throws OPCUAException {
        final EndpointDescription description = EndpointDescription.builder().endpointUrl("opc.tcp://local:200").build();
        expect(retryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(Collections.singletonList(description))
                .once();
        expect(securityModule.createClient(anyString(), anyObject()))
                .andThrow(new ConfigurationException(ExceptionContext.AUTH_ERROR))
                .anyTimes();
        replay(retryDelegate, securityModule);
        assertThrows(OPCUAException.class, () -> endpoint.initialize("test"));
    }

    @Test
    public void successfulSecurityModuleShouldSetURI() throws OPCUAException {
        initializeEndpoint("test");
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void addSessionActivityListenerShouldSendActiveUnlessDisconnected() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(true, l);
        assertEquals(0, l.getSessionActiveLatch().getCount());
    }

    @Test
    public void addSessionActivityListenerShouldNotSendActiveIfDisconnected() throws OPCUAException {
        initializeEndpoint("test");
        ((SessionActivityListener)endpoint).onSessionInactive(null);
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(true, l);
        assertEquals(1, l.getSessionActiveLatch().getCount());
    }

    @Test
    public void twoAddSessionActivityListenerShouldOnlySendOneActive() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(2, 1);
        endpoint.manageSessionActivityListener(true, l);
        endpoint.manageSessionActivityListener(true, l);
        assertEquals(1, l.getSessionActiveLatch().getCount());
    }

    @Test
    public void removeSessionActivityListenerShouldNotSendActiveOrInactive() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(false, l);
        assertEquals(1, l.getSessionActiveLatch().getCount());
        assertEquals(1, l.getSessionInactiveLatch().getCount());
    }


    @Test
    public void addAndRemoveSessionActivityListenerShouldOnlySendFirstActive() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(true, l);
        endpoint.manageSessionActivityListener(false, l);
        assertEquals(0, l.getSessionActiveLatch().getCount());
        assertEquals(1, l.getSessionInactiveLatch().getCount());
    }

    private void initializeEndpoint(String uri) throws OPCUAException {
        final EndpointDescription description = EndpointDescription.builder().endpointUrl("opc.tcp://local:200").build();
        expect(retryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(Collections.singletonList(description))
                .once();
        expect(securityModule.createClient(anyString(), anyObject()))
                .andReturn(mockClient)
                .anyTimes();
        replay(retryDelegate, securityModule);
        endpoint.initialize(uri);
    }

    @Getter
    private static class TestSessionActivityListener implements SessionActivityListener {
        CountDownLatch sessionActiveLatch;
        CountDownLatch sessionInactiveLatch;

        public TestSessionActivityListener(int activeLatchCount, int inactiveLatchCount) {
            sessionActiveLatch = new CountDownLatch(activeLatchCount);
            sessionInactiveLatch = new CountDownLatch(inactiveLatchCount);
        }

        @Override
        public void onSessionActive(UaSession session) {
            sessionActiveLatch.countDown();
        }

        @Override
        public void onSessionInactive(UaSession session) {
            sessionActiveLatch.countDown();
        }

    }

}
