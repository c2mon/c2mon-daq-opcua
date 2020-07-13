package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.TestUtils;
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

public class TagEventsTest extends TagHandlerTestBase {

    CompletableFuture<Long> tagUpdate;
    CompletableFuture<Long> tagInvalid;

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        super.setUp();
        proxy.connect(uri);
        tagUpdate = listener.getTagUpdate();
        tagInvalid = listener.getTagInvalid();
    }

    @Test
    public void subscribeWithInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertEquals(tag1.getId(), tagInvalid.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithValidTagShouldNotInformSender () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertFalse(tagInvalid.isDone());
    }

    @Test
    public void refreshInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        endpoint.setReturnGoodStatusCodes(false);
        controller.refreshDataTag(tag1);
        assertEquals(tag1.getId(), tagInvalid.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshValidShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        controller.refreshDataTag(tag1);
        assertEquals(tag1.getId(), tagUpdate.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void validSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.GOOD, tag1);
        controller.subscribeTags(Collections.singletonList(tag1));
        assertEquals(tag1.getId(), tagUpdate.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagInvalid.isDone());
    }

    @Test
    public void invalidSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.BAD, tag1);
        controller.subscribeTags(Collections.singletonList(tag1));
        assertEquals(tag1.getId(), tagInvalid.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagUpdate.isDone());
    }
}
