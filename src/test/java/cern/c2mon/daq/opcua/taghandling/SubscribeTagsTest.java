/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
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
