/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.daq.opcua.connection.endpoint;

import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static it.ServerTagProvider.*;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.*;

public class SubscribeTagsToEndpointTest extends EndpointSubscriptionBase {

    @Test
    public void addEmptyDataTagsCollectionShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> endpoint.subscribeTags(new ArrayList<>()).get());
    }

    @Test
    public void addSingleDataTagInKnownGroupShouldRegisterTagInGroup() throws OPCCommunicationException, ExecutionException, InterruptedException {
        ISourceDataTag tag = createAndMockTagsInKnownGroups(1, RandomUnsignedInt32).get(0);
        replayAndVerifyMocks(tag, client, subscription);

        SubscriptionGroup group = (SubscriptionGroup) tagSubscriptionMapper.getGroups().toArray()[0];
        assertTrue(group.contains(tagSubscriptionMapper.getDefinition(tag)));
    }

    @Test
    public void addSingleDataTagInKnownGroupShouldRegisterTagInMapper() throws OPCCommunicationException, ExecutionException, InterruptedException {
        ISourceDataTag tag = createAndMockTagsInKnownGroups(1, RandomUnsignedInt32).get(0);
        replayAndVerifyMocks(tag, client, subscription);

    }

    @Test
    public void addSeveralDataTagsInSameKnownGroupShouldRegisterTagsInGroup() throws ExecutionException, InterruptedException {
        List<ISourceDataTag> tags = createAndMockTagsInKnownGroups(1, RandomUnsignedInt32, DipData);
        replayAndVerifyMocks(tags, client, subscription);

        SubscriptionGroup group = (SubscriptionGroup) tagSubscriptionMapper.getGroups().toArray()[0];
        for (ISourceDataTag tag : tags) {
            assertTrue(group.contains(tagSubscriptionMapper.getDefinition(tag)));
        }
    }


    @Test
    public void addSeveralDataTagsInKnownGroupShouldRegisterTagInMapper() throws OPCCommunicationException, ExecutionException, InterruptedException {
        List<ISourceDataTag> tags = createAndMockTagsInKnownGroups(1, RandomUnsignedInt32, DipData);
        replayAndVerifyMocks(tags, client, subscription);

    }

    private void replayAndVerifyMocks(ISourceDataTag tag, Object... mocks) throws ExecutionException, InterruptedException {
        replay(mocks);
        endpoint.subscribeTag(tag).get();
        verify(mocks);
    }

    private void replayAndVerifyMocks(List<ISourceDataTag> tags, Object... mocks) throws ExecutionException, InterruptedException {
        replay(mocks);
        endpoint.subscribeTags(tags).get();
        verify(mocks);
    }


    @Test
    public void addSeveralDataTagsInDifferentKnownGroups() throws ExecutionException, InterruptedException {
        List<ISourceDataTag> tags = createAndMockTagsInKnownGroups(2, RandomUnsignedInt32, DipData, RandomBoolean);
        replayAndVerifyMocks(tags, client, subscription);



    }

    @Test
    public void addSingleDataTagInNewGroup() {
        List<ISourceDataTag> tags = createAndMockTagsInNewGroups(1, RandomUnsignedInt32);

        replay(client, subscription, subscriptionManager);
        assertDoesNotThrow(() -> endpoint.subscribeTag(tags.get(0)).get());
        verify(client, subscription, subscriptionManager);
    }

    @Test
    public void addSeveralDataTagsInSameNewGroup() {
        List<ISourceDataTag> tags = createAndMockTagsInNewGroups(1, RandomUnsignedInt32, DipData);

        replay(client, subscription, subscriptionManager);
        assertDoesNotThrow(() -> endpoint.subscribeTags(tags).get());
        verify(client, subscription, subscriptionManager);
    }

    @Test
    public void addSeveralDataTagsInDifferentNewGroups() throws ExecutionException, InterruptedException {
        List<ISourceDataTag> tags = createAndMockTagsInNewGroups(3, RandomUnsignedInt32, DipData, RandomBoolean);

        replay(client, subscription, subscriptionManager);
        endpoint.subscribeTags(tags).get();
        verify(client, subscription, subscriptionManager);
    }

}
