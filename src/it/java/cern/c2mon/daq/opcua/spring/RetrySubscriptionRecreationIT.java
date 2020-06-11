package cern.c2mon.daq.opcua.spring;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cern.c2mon.daq.opcua.testutils.ServerTagFactory.AlternatingBoolean;
import static cern.c2mon.daq.opcua.testutils.ServerTagFactory.RandomUnsignedInt32;
import static org.easymock.EasyMock.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:retry.properties")
@ExtendWith(SpringExtension.class)
public class RetrySubscriptionRecreationIT {

    private final Endpoint endpointMock = createNiceMock(Endpoint.class);
    private final UaSubscription subscriptionMock = createNiceMock(UaSubscription.class);
    private final int numAttempts = 3;
    @Autowired
    Controller controller;
    @Autowired
    TagSubscriptionMapper mapper;
    @Autowired
    EndpointSubscriptionListener listener;
    @Autowired
    Endpoint endpoint;
    @Value("${app.retryDelay}")
    int delay;

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        endpointMock.initialize(anyString());
        EasyMock.expectLastCall().anyTimes();
        EasyMock.replay(endpointMock);
        ReflectionTestUtils.setField(controller, "failover", TestUtils.getFailoverProxy(endpointMock));
        ReflectionTestUtils.setField(controller, "stopped", new AtomicBoolean(false));
        EasyMock.reset(endpointMock);

    }

    @AfterEach
    public void tearDown() {
        ReflectionTestUtils.setField(controller, "stopped", new AtomicBoolean(true));
        ReflectionTestUtils.setField(controller, "failover", TestUtils.getFailoverProxy(endpoint));
    }


    @Test
    public void callOnceEveryDelayOnCommunicationException() throws OPCUAException {
        expect(endpointMock.createSubscription(anyInt()))
                .andThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION))
                .times(numAttempts);
        replay(endpointMock);
        setUpMapper();
        try {
            listener.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD);
        } catch (Exception e) {
            //expected
        }

        //add a buffer to make sure that the nth execution finishes
        CompletableFuture.runAsync(() -> verify(endpointMock),
                CompletableFuture.delayedExecutor(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS))
                .join();

    }

    @Test
    public void stopCallingOnInterruptedThread() throws OPCUAException {
        expect(endpointMock.createSubscription(anyInt()))
                .andThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION))
                .times(numAttempts);
        replay(endpointMock);
        setUpMapper();
        final Thread thread = new Thread(() -> listener.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD));
        thread.start();
        CompletableFuture.runAsync(thread::interrupt, CompletableFuture.delayedExecutor(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS))
                .thenRun(() -> verify(endpointMock)).join();
    }


    @Test
    public void callOnceOnConfigurationException() {
        // not setting up the mapper causes a configuration exception to be thrown before the endpoint is ever called.
        replay(endpointMock);
        listener.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD);
        verify(endpointMock);
    }


    @Test
    public void statusChangedShouldNotRetryIfStatusCodeIsBadButNotTimeout() {
        replay(endpointMock);
        setUpMapper();
        listener.onStatusChanged(subscriptionMock, StatusCode.BAD);
        //ass a buffer to make sure that the nth execution finishes
        verify(endpointMock);
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
