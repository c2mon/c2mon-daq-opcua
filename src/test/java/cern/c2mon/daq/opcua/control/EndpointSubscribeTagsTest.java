package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointSubscribeTagsTest extends EndpointTestBase{

    @Test
    public void subscribeNewTagShouldSubscribeTagInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeTwoNewTagsIterativelyShouldSubscribeBothInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void subscribeTwoNewTagsWithDifferentDeadbandsIterativelyShouldSubscribeBothInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagWithBadStatusCodeShouldUnsubscribeDataTag () {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeNewTagAsCollectionShouldSubscribeTagInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }


    @Test
    public void subscribeTagsWithDifferentDeadbandShouldSubscribeBothTags () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagsWithSameDeadbandShouldSubscribeBothTags () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void badStatusCodeShouldUnsubscribeDataTag() {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1, tag2);
        assertFalse(mapper.isSubscribed(tag1) || mapper.isSubscribed(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() throws ConfigurationException {
        mocker.mockGoodAndBadStatusCodesAndReplay(new ISourceDataTag[]{tag1}, new ISourceDataTag[]{tag2});
        subscribeTags(tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && !mapper.isSubscribed(tag2));
    }

    @Test
    public void removeUnsubscribedDataTagShouldThrowError() {
        assertThrows(IllegalArgumentException.class, () -> endpoint.removeDataTag(tag1));
    }

    @Test
    public void removeDataTagShouldUnsubscribeDataTag() {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD,tag1);
        endpoint.removeDataTag(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void removeOneOfTwoTagsShouldUnsubscribeDataTag() {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        endpoint.removeDataTag(tag1);
        assertTrue(!mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }
}