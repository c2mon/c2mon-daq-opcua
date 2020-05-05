package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TagEventsTest extends ControllerTestBase {

    public static int TIMEOUT = 200;
    CompletableFuture<Long> tagUpdate;
    CompletableFuture<Long> tagInvalid;

    @BeforeEach
    public void setUp() {
        super.setUp();
        try {
            controller.initialize(uri, Collections.emptyList());
        } catch (ConfigurationException e) {
            // swallow exception from empty SourceTag list. Would make mocking more complex.
        }
        ServerTestListener.TestListener l = ServerTestListener.subscribeAndReturnListener(publisher);
        tagUpdate = l.getTagUpdate();
        tagInvalid = l.getTagInvalid();
    }

    @Test
    public void subscribeWithInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertEquals(tag1.getId(), tagInvalid.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithValidTagShouldNotInformSender () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertFalse(tagInvalid.isDone());
    }

    @Test
    public void refreshInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        endpoint.setReturnGoodStatusCodes(false);
        controller.refreshDataTag(tag1);
        assertEquals(tag1.getId(), tagInvalid.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshValidShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        controller.refreshDataTag(tag1);
        assertEquals(tag1.getId(), tagUpdate.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void validSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.GOOD, tag1);
        controller.subscribeTags(Collections.singletonList(tag1));
        assertEquals(tag1.getId(), tagUpdate.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagInvalid.isDone());
    }

    @Test
    public void invalidSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.BAD, tag1);
        controller.subscribeTags(Collections.singletonList(tag1));
        assertEquals(tag1.getId(), tagInvalid.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagUpdate.isDone());
    }
}
