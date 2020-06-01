package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerImpl;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static cern.c2mon.daq.opcua.testutils.ServerTagFactory.AlternatingBoolean;
import static cern.c2mon.daq.opcua.testutils.ServerTagFactory.RandomUnsignedInt32;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointSubscriptionListenerTest {

    private final int delay = 100;
    private final int numAttempts = 3;
    private final TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    private final EndpointSubscriptionListener listener = new EndpointSubscriptionListener();
    private final UaSubscription subscriptionMock = createNiceMock(UaSubscription.class);
    private final Endpoint endpoint = createNiceMock(Endpoint.class);
    private final Controller controller = new ControllerImpl(mapper, new TestListeners.TestListener(), endpoint);


    @BeforeEach
    public void setUp() {
        listener.setController(controller);
        ReflectionTestUtils.setField(listener, "delay", delay);
        ReflectionTestUtils.setField(controller, "stopped", new AtomicBoolean(false));
    }

    @Test
    public void callOnceEveryDelayOnCommunicationException() throws OPCUAException, InterruptedException {
        expect(endpoint.createSubscription(anyInt()))
                .andThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION))
                .times(numAttempts);
        replay(endpoint);
        setUpMapper();
        final var f = CompletableFuture.runAsync(() -> listener.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD));
        //add a buffer to make sure that the nth execution finishes
        assertThrows(TimeoutException.class, () -> f.get(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS));
        verify(endpoint);
    }

    @Test
    public void stopCallingOnInterruptedExceptionConfigurationException() throws OPCUAException, InterruptedException {
        expect(endpoint.createSubscription(anyInt()))
                .andThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION))
                .times(numAttempts);
        replay(endpoint);
        setUpMapper();
        final Thread thread = new Thread(() -> listener.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD));
        thread.start();
        CompletableFuture.runAsync(thread::interrupt, CompletableFuture.delayedExecutor(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS))
                .thenRun(() -> verify(endpoint)).join();
    }


    @Test
    public void callOnceOnConfigurationException() {
        // not setting up the mapper causes a configuration exception to be thrown before the endpoint is ever called.
        replay(endpoint);
        listener.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD);
        verify(endpoint);
    }

    @Test
    public void statusChangedShouldNotRetryIfStatusCodeIsBadButNotTimeout() {
        replay(endpoint);
        setUpMapper();
        listener.onStatusChanged(subscriptionMock, StatusCode.BAD);
        //ass a buffer to make sure that the nth execution finishes
        verify(endpoint);
    }

    @Test
    public void statusChangedShouldCallRetryIfStatusCodeIsTimeoutException() throws OPCUAException, InterruptedException {
        expect(endpoint.createSubscription(anyInt()))
                .andThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION))
                .times(numAttempts);
        replay(endpoint);
        setUpMapper();
        final var f = CompletableFuture.runAsync(() -> listener.onStatusChanged(subscriptionMock, new StatusCode(StatusCodes.Bad_Timeout)));
        assertThrows(TimeoutException.class, () -> f.get(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS));
        verify(endpoint);
    }


    private void setUpMapper() {
        for (ServerTagFactory tagConfig : new ServerTagFactory[]{AlternatingBoolean, RandomUnsignedInt32}) {
            final ISourceDataTag tag = tagConfig.createDataTag();
            mapper.getOrCreateDefinition(tag);
            mapper.addTagToGroup(tag.getId());
            mapper.getGroup(tag).setSubscription(subscriptionMock);
        }
    }
}