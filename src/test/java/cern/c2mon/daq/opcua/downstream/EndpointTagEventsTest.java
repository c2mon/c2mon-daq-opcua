package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
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

public class EndpointTagEventsTest extends EndpointTestBase {

    public static int TIMEOUT = 200;
    CompletableFuture<Object> newTagValueFuture = new CompletableFuture<>();
    CompletableFuture<Object> tagInvalidFuture = new CompletableFuture<>();

    @BeforeEach
    public void setup() {
        super.setup();
        endpoint.initialize(false);
        listenForTagEvent();
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
        assertEquals(tag1, newTagValueFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void validSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        mocker.mockValueConsumerAndSubscribeTag(StatusCode.GOOD, tag1);
        endpoint.subscribeTag(tag1);
        assertEquals(tag1, newTagValueFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagInvalidFuture.isDone());
    }

    @Test
    public void invalidSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        mocker.mockValueConsumerAndSubscribeTag(StatusCode.BAD, tag1);
        endpoint.subscribeTag(tag1);
        assertEquals(tag1, tagInvalidFuture.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(newTagValueFuture.isDone());
    }

    private void listenForTagEvent () {
        publisher.subscribe(new EndpointListener() {
            @Override
            public void update (EquipmentState state) { }

            @Override
            public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
                newTagValueFuture.complete(dataTag);
            }

            @Override
            public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
                tagInvalidFuture.complete(dataTag);
            }

            @Override
            public void initialize (IEquipmentMessageSender sender) {

            }
        });
    }

}
