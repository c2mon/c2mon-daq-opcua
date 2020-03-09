package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointSubscribeTagsTest extends EndpointTestBase{

    @Test
    public void subscribeTagsWithEmptyCollectionShouldThrowError() {
        assertThrows(ConfigurationException.class, () -> endpoint.subscribeTags(Collections.emptyList()));
    }

    @Test
    public void subscribeNewTagShouldSubscribeTagInMapper () {
        subscribeTagAndMockGoodStatusCode(tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeTwoNewTagsIterativelyShouldSubscribeBothInMapper () {
        mockGoodStatusCodesAndClientHandles(tag1, tag2);
        subscribeTag(tag1);
        subscribeTag(tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void subscribeTwoNewTagsWithDifferentDeadbandsIterativelyShouldSubscribeBothInMapper () {
        mockStatusCodesAndClientHandles(new ISourceDataTag[]{tag1, tagWithDeadband}, new ISourceDataTag[]{});
        subscribeTag(tag1);
        subscribeTag(tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagWithBadStatusCodeShouldUnsubscribeDataTag () {
        mockBadStatusCodesAndClientHandles(tag1);
        subscribeTag(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeNewTagAsCollectionShouldSubscribeTagInMapper () {
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
    public void badStatusCodeShouldUnsubscribeDataTag() throws ConfigurationException {
        mockBadStatusCodesAndClientHandles(tag1);
        subscribeTags(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() throws ConfigurationException {
        mockBadStatusCodesAndClientHandles(tag1, tag2);
        subscribeTags(tag1, tag2);
        assertFalse(mapper.isSubscribed(tag1) || mapper.isSubscribed(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() throws ConfigurationException {
        mockStatusCodesAndClientHandles(new ISourceDataTag[]{tag1}, new ISourceDataTag[]{tag2});
        subscribeTags(tag1, tag2);
        assertTrue(mapper.isSubscribed(tag1) && !mapper.isSubscribed(tag2));
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

    @SneakyThrows
    protected void subscribeTagsAndMockGoodStatusCode (ISourceDataTag... tags)  {
        mockGoodStatusCodesAndClientHandles(tags);
        super.subscribeTags(tags);
    }

    protected void subscribeTagAndMockGoodStatusCode (ISourceDataTag tag) {
        mockStatusCodesAndClientHandles(new ISourceDataTag[]{tag}, new ISourceDataTag[]{});
        super.subscribeTag(tag);
    }
}
