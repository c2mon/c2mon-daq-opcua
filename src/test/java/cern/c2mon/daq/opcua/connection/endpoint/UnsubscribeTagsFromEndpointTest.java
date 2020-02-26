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

import cern.c2mon.shared.common.datatag.ISourceDataTag;
import com.google.common.collect.ImmutableList;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static it.ServerTagProvider.RandomUnsignedInt32;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UnsubscribeTagsFromEndpointTest extends EndpointSubscriptionBase {

    private ImmutableList<UaMonitoredItem> monitoredItems = createMock(ImmutableList.class);

    @Test
    public void removeUnknownTagShouldThrowException() {
        ISourceDataTag tag = RandomUnsignedInt32.createDataTag();

        assertThrows(IllegalArgumentException.class, () -> endpoint.removeDataTag(tag));
    }

    @Test
    public void removeTagInUnsubscribedGroupShouldThrowException() {
        ISourceDataTag tag = RandomUnsignedInt32.createDataTag();
        tagSubscriptionMapper.toGroup(tag);

        assertThrows(IllegalArgumentException.class, () -> endpoint.removeDataTag(tag));
    }

    @Test
    public void addAndRemoveSingleTag() {
        List<ISourceDataTag> tags = createAndMockTagsInKnownGroups(1, RandomUnsignedInt32);
        removeTags(1, tags);
    }

    private void removeTags(int nrOfGroups, List<ISourceDataTag> tags) {
        mockRemoveDefinition(tags.size());
        mockRemoveSubscription(nrOfGroups);
        tags.forEach(tag -> endpoint.removeDataTag(tag));
    }

    private void mockRemoveDefinition(int nrOfTags) {
        expect(subscription.getMonitoredItems()).andReturn(monitoredItems).times(nrOfTags);
    }

    private void mockRemoveSubscription(int nrOfGroups) {
        expect(client.getSubscriptionManager())
                .andReturn(subscriptionManager).times(nrOfGroups);
        expect(subscription.getSubscriptionId())
                .andReturn(UInteger.valueOf(1)).times(nrOfGroups);
        expect(subscriptionManager.deleteSubscription(anyObject(UInteger.class)))
                .andReturn(CompletableFuture.completedFuture(subscription)).times(nrOfGroups);
    }

}
