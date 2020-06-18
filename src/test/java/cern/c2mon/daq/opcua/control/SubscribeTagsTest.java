package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscribeTagsTest extends ControllerTestBase{

    @Test
    public void subscribeNewTagShouldSubscribeTagInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertTrue(mapper.getGroup(tag1).contains(tag1));
    }

    @Test
    public void subscribeTwoNewTagsIterativelyShouldSubscribeBothInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        assertTrue(mapper.getGroup(tag1).contains(tag1) && mapper.getGroup(tag2).contains(tag2));
    }

    @Test
    public void subscribeTwoNewTagsWithDifferentDeadbandsIterativelyShouldSubscribeBothInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        assertTrue(mapper.getGroup(tag1).contains(tag1) && mapper.getGroup(tagWithDeadband).contains(tagWithDeadband));
    }

    @Test
    public void subscribeTagWithBadStatusCodeShouldUnsubscribeDataTag () {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertFalse(mapper.getGroup(tag1).contains(tag1));
    }

    @Test
    public void subscribeNewTagAsCollectionShouldSubscribeTagInMapper () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertTrue(mapper.getGroup(tag1).contains(tag1));
    }

    @Test
    public void subscribeTagsWithDifferentDeadbandShouldSubscribeBothTags () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tagWithDeadband);
        assertTrue(mapper.getGroup(tag1).contains(tag1) && mapper.getGroup(tagWithDeadband).contains(tagWithDeadband));
    }

    @Test
    public void subscribeTagsWithSameDeadbandShouldSubscribeBothTags () {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        assertTrue(mapper.getGroup(tag1).contains(tag1) && mapper.getGroup(tag2).contains(tag2));
    }

    @Test
    public void badStatusCodeShouldUnsubscribeDataTag() {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertFalse(mapper.getGroup(tag1).contains(tag1));
    }

    @Test
    public void twoTagsWithBadSubscriptionStatusCodesShouldUnsubscribeBoth() {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1, tag2);
        assertFalse(mapper.getGroup(tag1).contains(tag1) || mapper.getGroup(tag2).contains(tag2));
    }

    @Test
    public void twoTagsWithOneBadStatusCodeShouldUnsubscribeOne() throws ConfigurationException {
        mocker.mockGoodAndBadStatusCodesAndReplay(new ISourceDataTag[]{tag1}, new ISourceDataTag[]{tag2});
        subscribeTags(tag1, tag2);
        assertTrue(mapper.getGroup(tag1).contains(tag1) && !mapper.getGroup(tag2).contains(tag2));
    }
}
