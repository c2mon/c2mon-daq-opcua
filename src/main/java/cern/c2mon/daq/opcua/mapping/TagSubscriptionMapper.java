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
package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public interface TagSubscriptionMapper {

    Map<Long, ItemDefinition> getTagIdDefinitionMap();

    Map<SubscriptionGroup, List<ItemDefinition>> mapTagsToGroupsAndDefinitions(Collection<ISourceDataTag> dataTags);

    SubscriptionGroup getGroup (ISourceDataTag dataTag);

    SubscriptionGroup getGroup (UaSubscription subscription);

    SubscriptionGroup getOrCreateGroup(int timeDeadband);

    ItemDefinition getDefinition(Long tagId);

    long getTagId(ItemDefinition definition);

    ItemDefinition getOrCreateDefinition(ISourceDataTag tag);

    Long getTagId(UInteger clientHandle);

    void addTagToGroup (Long dataTag);

    boolean removeTag(ISourceDataTag dataTag);

    void clear();

    boolean isSubscribed(ISourceDataTag tag);
}

