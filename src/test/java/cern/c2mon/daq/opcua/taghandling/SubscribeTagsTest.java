package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscribeTagsTest extends TagHandlerTestBase {

    @Test
    public void subscribeNewTagShouldSubscribeTagInMapper () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertTrue(isSubscribed(tag1));
    }

    @Test
    public void subscribeTwoNewTagsIterativelyShouldSubscribeBothInMapper () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        assertTrue(isSubscribed(tag1) && isSubscribed(tag2));
    }

    @Test
    public void subscribeTwoNewTagsWithDifferentDeadbandsIterativelyShouldSubscribeBothInMapper () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        assertTrue(isSubscribed(tag1) && isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagWithBadStatusCodeShouldUnsubscribeDataTag () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertFalse(isSubscribed(tag1));
    }

    @Test
    public void subscribeNewTagAsCollectionShouldSubscribeTagInMapper () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertTrue(isSubscribed(tag1));
    }

    @Test
    public void subscribeTagsWithDifferentDeadbandShouldSubscribeBothTags () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        assertTrue(isSubscribed(tag1) && isSubscribed(tagWithDeadband));
    }

    @Test
    public void subscribeTagsWithSameDeadbandShouldSubscribeBothTags () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        assertTrue(isSubscribed(tag1) && isSubscribed(tag2));
    }

    @Test
    public void badStatusCodeShouldUnsubscribeDataTag() throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertFalse(isSubscribed(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1, tag2);
        assertFalse(isSubscribed(tag1) || isSubscribed(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() throws ConfigurationException {
        mocker.mockGoodAndBadStatusCodesAndReplay(new ISourceDataTag[]{tag1}, new ISourceDataTag[]{tag2});
        tagHandler.subscribeTags(Arrays.asList(tag1, tag2));
        assertTrue(isSubscribed(tag1) && !isSubscribed(tag2));
    }
}
