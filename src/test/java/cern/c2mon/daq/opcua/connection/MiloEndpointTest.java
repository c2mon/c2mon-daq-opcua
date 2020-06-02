package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.*;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NodeIdUnknown;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MiloEndpointTest {

    RetryDelegate delegate = new RetryDelegate();
    EndpointSubscriptionListener listener = new EndpointSubscriptionListener();
    Endpoint endpoint = new MiloEndpoint(null, null, listener, delegate);

    OpcUaClient mockClient = createMock(OpcUaClient.class);
    OpcUaSubscriptionManager managerMock = createMock(OpcUaSubscriptionManager.class);
    UaSubscription subscriptionMock = createNiceMock(UaSubscription.class);

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(endpoint, "client", mockClient);
        ReflectionTestUtils.setField(delegate, "maxRetryCount", 3);
        ReflectionTestUtils.setField(delegate, "timeout", 300);
        ReflectionTestUtils.setField(delegate, "retryDelay", 1000);
    }

    private void setUpDeleteSubscriptionMocks(CompletableFuture<UaSubscription> expected) {
        expect(mockClient.getSubscriptionManager()).andReturn(managerMock).anyTimes();
        expect(subscriptionMock.getSubscriptionId()).andReturn(null).anyTimes();
        expect(managerMock.deleteSubscription(anyObject())).andReturn(expected).anyTimes();
        replay(mockClient, managerMock, subscriptionMock);
    }

    @Test
    public void uaExceptionOrTypeConfigShouldThrowConfigurationException() {
        final UaException configUaException = new UaException(Bad_NodeIdUnknown);
        final CompletableFuture<UaSubscription> expected = CompletableFuture.failedFuture(configUaException);
        setUpDeleteSubscriptionMocks(expected);
        assertThrows(ConfigurationException.class, () -> endpoint.deleteSubscription(subscriptionMock));
    }

    @Test
    public void unknownHostShouldThrowConfigurationException() {
        final CompletableFuture<UaSubscription> expected = CompletableFuture.failedFuture(new UnknownHostException());
        setUpDeleteSubscriptionMocks(expected);
        assertThrows(ConfigurationException.class, () -> endpoint.deleteSubscription(subscriptionMock));
    }

    @Test
    public void anyOtherExceptionShouldThrowCommunicationException() {
        final CompletableFuture<UaSubscription> expected = CompletableFuture.failedFuture(new IllegalArgumentException());
        setUpDeleteSubscriptionMocks(expected);
        assertThrows(CommunicationException.class, () -> endpoint.deleteSubscription(subscriptionMock));
    }

    @Test
    public void alreadyAttemptingResubscriptionTooLongThrowsLongLostConnectionException() {
        ReflectionTestUtils.setField(delegate, "disconnectionInstant", 20);
        final var expectedException = new LongLostConnectionException(ExceptionContext.DELETE_SUBSCRIPTION, new IllegalArgumentException());
        final CompletableFuture<UaSubscription> expected = CompletableFuture.failedFuture(expectedException);
        setUpDeleteSubscriptionMocks(expected);
        assertThrows(LongLostConnectionException.class, () -> endpoint.deleteSubscription(subscriptionMock));
    }
}
