package cern.c2mon.daq.opcua.connection;

import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointSubscribeTagsTest extends EndpointTestBase{

    @Test
    public void subscribeTagsWithEmptyCollectionShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> endpoint.subscribeTags(Collections.emptyList()));
    }

    @Test
    public void subscribeNewTagShouldSubscribeTagInMapper () {
        subscribeTagsAndMockGoodStatusCode(tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeTagsWithDifferentDeadbandShouldSubscribeBothTags () {
        subscribeTagsAndMockGoodStatusCode(tag1, tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagsWithSameDeadbandShouldSubscribeBothTags () {
        subscribeTagsAndMockGoodStatusCode(tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void badStatusCodeShouldUnsubscribeDataTag() {
        subscribeTags(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() {
        subscribeTags(tag1, tag2);
        assertFalse(mapper.isSubscribed(tag1) || mapper.isSubscribed(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() {
        mockGoodSubscriptionStatusCode(tag2);
        subscribeTags(tag1, tag2);
        assertTrue(!mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void removeUnsubscribedDataTagShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> endpoint.removeDataTag(tag1));
    }

    @Test
    public void removeDataTagShouldUnsubscribeDataTag() {
        subscribeTagsAndMockGoodStatusCode(tag1);
        endpoint.removeDataTag(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void removeOneOfTwoTagsShouldUnsubscribeDataTag() {
        subscribeTagsAndMockGoodStatusCode(tag1, tag2);
        endpoint.removeDataTag(tag1);
        assertTrue(!mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    protected void subscribeTagsAndMockGoodStatusCode (ISourceDataTag... tags) {
        mockGoodSubscriptionStatusCode(tags);
        super.subscribeTags(tags);
    }
}
