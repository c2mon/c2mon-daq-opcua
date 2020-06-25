package cern.c2mon.daq.opcua.spring;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import com.google.common.collect.BiMap;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import static cern.c2mon.daq.opcua.testutils.EdgeTagFactory.AlternatingBoolean;
import static cern.c2mon.daq.opcua.testutils.EdgeTagFactory.RandomUnsignedInt32;
import static org.easymock.EasyMock.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:retry.properties")
@ExtendWith(SpringExtension.class)
public class RetrySubscriptionRecreationIT {

    OpcUaClient clientMock = createNiceMock(OpcUaClient.class);
    private final UaSubscription subscriptionMock = createNiceMock(UaSubscription.class);
    private final int numAttempts = 3;
    @Autowired TagSubscriptionMapper mapper;
    @Autowired UaSubscriptionManager.SubscriptionListener endpoint;
    @Value("${app.retryDelay}")
    int delay;

    @BeforeEach
    public void setUp() {
        mapper.clear();
        EasyMock.reset(subscriptionMock);
        ReflectionTestUtils.setField(endpoint, "client", clientMock);
        final BiMap<Integer, UaSubscription> subscriptionMap = (BiMap<Integer, UaSubscription>) ReflectionTestUtils.getField(endpoint, "subscriptionMap");
        subscriptionMap.put(0, subscriptionMock);
    }

    @Test
    public void callOnceEveryDelayOnCommunicationException() throws InterruptedException, ExecutionException, TimeoutException {
        mockNAttempts(numAttempts);
        CompletableFuture.runAsync(() -> endpoint.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD));
        //add a buffer to make sure that the nth execution finishes
        CompletableFuture.runAsync(() -> verify(clientMock),
                CompletableFuture.delayedExecutor(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS))
                .get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

    }

    @Test
    public void stopCallingOnInterruptedThread() throws InterruptedException, ExecutionException, TimeoutException {
        mockNAttempts(numAttempts);
        final Thread thread = new Thread(() -> endpoint.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD));
        thread.start();
        CompletableFuture.runAsync(thread::interrupt, CompletableFuture.delayedExecutor(Math.round(delay * numAttempts), TimeUnit.MILLISECONDS))
                .thenRun(() -> verify(clientMock))
                .get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
    }

    @Test
    public void callOnceOnConfigurationException() {
        // not setting up the mapper causes a configuration exception to be thrown before the endpoint is ever called.
        replay(clientMock);
        endpoint.onSubscriptionTransferFailed(subscriptionMock, StatusCode.GOOD);
        verify(clientMock);
    }

    @Test
    public void statusChangedShouldNotRetryIfStatusCodeIsBadButNotTimeout() {
        replay(clientMock);
        setUpMapper();
        endpoint.onStatusChanged(subscriptionMock, StatusCode.BAD);
        //ass a buffer to make sure that the nth execution finishes
        verify(clientMock);
    }

    private void mockNAttempts(int numAttempts) {
        final OpcUaSubscriptionManager managerMock = createNiceMock(OpcUaSubscriptionManager.class);
        expect(clientMock.getSubscriptionManager()).andReturn(managerMock).anyTimes();
        expect(managerMock.deleteSubscription(anyObject())).andReturn(CompletableFuture.completedFuture(null)).anyTimes();
        expect(subscriptionMock.createMonitoredItems(anyObject(), anyObject(), (BiConsumer<UaMonitoredItem, Integer>) anyObject()))
                .andReturn(CompletableFuture.failedFuture(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION)))
                .times(numAttempts);
        replay(clientMock, managerMock);
        setUpMapper();
    }

    private void setUpMapper() {
        for (EdgeTagFactory tagConfig : new EdgeTagFactory[]{AlternatingBoolean, RandomUnsignedInt32}) {
            final ISourceDataTag tag = tagConfig.createDataTag();
            mapper.getOrCreateDefinition(tag);
            mapper.addTagToGroup(tag.getId());
        }
    }
}
