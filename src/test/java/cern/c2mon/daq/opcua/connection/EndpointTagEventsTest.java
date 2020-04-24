package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.TAG_INVALID;
import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.TAG_UPDATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EndpointTagEventsTest extends EndpointTestBase {

    public static int TIMEOUT = 200;
    CompletableFuture<Object> tagUpdateFuture;
    CompletableFuture<Object> tagInvalidFuture;

    @BeforeEach
    public void setup() {
        super.setup();
        endpoint.connect(false);
        Map<ServerTestListener.Target, CompletableFuture<Object>> f = ServerTestListener.createListenerAndReturnFutures(publisher);
        tagUpdateFuture = f.get(TAG_UPDATE);
        tagInvalidFuture = f.get(TAG_INVALID);
    }

    @Test
    public void subscribeWithInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertEquals(tag1, tagInvalidFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithValidTagShouldNotInformSender () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertFalse(tagInvalidFuture.isDone());
    }

    @Test
    public void refreshInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        client.setReturnGoodStatusCodes(false);
        endpoint.refreshDataTags(Collections.singletonList(tag1));
        assertEquals(tag1, tagInvalidFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshValidShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        endpoint.refreshDataTags(Collections.singletonList(tag1));
        assertEquals(tag1, tagUpdateFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void validSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.GOOD, tag1);
        endpoint.subscribeTag(tag1);
        assertEquals(tag1, tagUpdateFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagInvalidFuture.isDone());
    }

    @Test
    public void invalidSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.BAD, tag1);
        endpoint.subscribeTag(tag1);
        assertEquals(tag1, tagInvalidFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagUpdateFuture.isDone());
    }
}
