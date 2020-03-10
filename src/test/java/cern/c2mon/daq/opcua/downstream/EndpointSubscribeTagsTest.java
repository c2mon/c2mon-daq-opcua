package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
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
        subscribeTagsAndMockGoodStatusCode(tag1);
        assertTrue(mapper.isSubscribed(tag1));
    }

    @Test
    public void subscribeTwoNewTagsIterativelyShouldSubscribeBothInMapper () {
        mockStatusCode(StatusCode.GOOD, tag1, tag2);
        subscribeTag(tag1);
        subscribeTag(tag2);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }

    @Test
    public void subscribeTwoNewTagsWithDifferentDeadbandsIterativelyShouldSubscribeBothInMapper () {
        mockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        subscribeTag(tag1);
        subscribeTag(tagWithDeadband);
        assertTrue(mapper.isSubscribed(tag1) && mapper.isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagWithBadStatusCodeShouldUnsubscribeDataTag () {
        mockStatusCode(StatusCode.BAD, tag1);
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
        mockStatusCode(StatusCode.BAD, tag1);
        subscribeTags(tag1);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() throws ConfigurationException {
        mockStatusCode(StatusCode.BAD, tag1, tag2);
        subscribeTags(tag1, tag2);
        assertFalse(mapper.isSubscribed(tag1) || mapper.isSubscribed(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() throws ConfigurationException {
        mockGoodAndBadStatusCodes(new ISourceDataTag[]{tag1}, new ISourceDataTag[]{tag2});
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
        mockStatusCode(StatusCode.GOOD, tags);
        super.subscribeTags(tags);
    }
}
