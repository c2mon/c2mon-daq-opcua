package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.*;

public class EndpointSubscribeTagsTest extends EndpointTestBase{

    @Test
    public void subscribeTagsWithEmptyCollectionShouldThrowError() {
        assertThrows(ConfigurationException.class, () -> endpoint.subscribeTags(Collections.emptyList()));
    }

    @Test
    public void subscribeNewTagShouldSubscribeTagInMapper () throws ExecutionException, InterruptedException {
        subscribeTagAndMockGoodStatusCode(tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeTwoNewTagsIterativelyShouldSubscribeBothInMapper () throws ExecutionException, InterruptedException {
        mockStatusCodes(new ISourceDataTag[]{tag1, tag2}, new ISourceDataTag[]{});
        subscribeTag(tag1);
        subscribeTag(tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void subscribeTwoNewTagsWithDifferentDeadbandsIterativelyShouldSubscribeBothInMapper () throws ExecutionException, InterruptedException {
        mockStatusCodes(new ISourceDataTag[]{tag1, tagWithDeadband}, new ISourceDataTag[]{});
        subscribeTag(tag1);
        subscribeTag(tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagWithBadStatusCodeShouldUnsubscribeDataTag () throws ExecutionException, InterruptedException {
        mockStatusCodes(new ISourceDataTag[]{}, new ISourceDataTag[]{tag1});
        subscribeTag(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeNewTagAsCollectionShouldSubscribeTagInMapper () throws ExecutionException, InterruptedException {
        subscribeTagsAndMockGoodStatusCode(tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }


    @Test
    public void subscribeTagsWithDifferentDeadbandShouldSubscribeBothTags () throws ExecutionException, InterruptedException {
        subscribeTagsAndMockGoodStatusCode(tag1, tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagsWithSameDeadbandShouldSubscribeBothTags () throws ExecutionException, InterruptedException {
        subscribeTagsAndMockGoodStatusCode(tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void badStatusCodeShouldUnsubscribeDataTag() throws ExecutionException, InterruptedException, ConfigurationException {
        mockStatusCodes(new ISourceDataTag[]{}, new ISourceDataTag[]{tag1});
        subscribeTags(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() throws ExecutionException, InterruptedException, ConfigurationException {
        mockStatusCodes(new ISourceDataTag[]{}, new ISourceDataTag[]{tag1, tag2});
        subscribeTags(tag1, tag2);
        assertFalse(mapper.isSubscribed(tag1) || mapper.isSubscribed(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() throws ExecutionException, InterruptedException, ConfigurationException {
        mockStatusCodes(new ISourceDataTag[]{tag1}, new ISourceDataTag[]{tag2});
        subscribeTags(tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && !mapper.isSubscribed(tag2));
    }

    @Test
    public void removeUnsubscribedDataTagShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> endpoint.removeDataTag(tag1));
    }

    @Test
    public void removeDataTagShouldUnsubscribeDataTag() throws ExecutionException, InterruptedException {
        subscribeTagsAndMockGoodStatusCode(tag1);
        endpoint.removeDataTag(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void removeOneOfTwoTagsShouldUnsubscribeDataTag() throws ExecutionException, InterruptedException {
        subscribeTagsAndMockGoodStatusCode(tag1, tag2);
        endpoint.removeDataTag(tag1);
        assertTrue(!mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @SneakyThrows
    protected void subscribeTagsAndMockGoodStatusCode (ISourceDataTag... tags)  {
        mockStatusCodes(tags, new ISourceDataTag[]{});
        super.subscribeTags(tags);
    }

    protected void subscribeTagAndMockGoodStatusCode (ISourceDataTag tag) throws ExecutionException, InterruptedException {
        mockStatusCodes(new ISourceDataTag[]{tag}, new ISourceDataTag[]{});
        super.subscribeTag(tag);
    }

    protected void mockStatusCodes(ISourceDataTag[] goodTags, ISourceDataTag[] badTags) {
        UaMonitoredItem monitoredItem = client.getMonitoredItem();
        mockSubscriptionStatusCode(monitoredItem, StatusCode.GOOD, goodTags);
        mockSubscriptionStatusCode(monitoredItem, StatusCode.BAD, badTags);
        replay(monitoredItem);

    }
}
