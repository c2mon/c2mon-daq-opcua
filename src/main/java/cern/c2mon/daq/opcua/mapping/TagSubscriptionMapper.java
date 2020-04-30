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

    Collection<SubscriptionGroup> getGroups();

    Map<SubscriptionGroup, List<DataTagDefinition>> maptoGroupsWithDefinitions (Collection<ISourceDataTag> dataTags);

    SubscriptionGroup getGroup (ISourceDataTag dataTags);

    SubscriptionGroup getGroup (UaSubscription subscription);

    DataTagDefinition getDefinition(ISourceDataTag dataTag);

    ISourceDataTag getTag (UInteger clientHandle);

    void addDefinitionsToGroups (Collection<DataTagDefinition> definitions);

    void addDefinitionToGroup (DataTagDefinition definition);

    void addTagToGroup (ISourceDataTag dataTag);

    void removeTagFromGroup (ISourceDataTag dataTag);

    void clear();

    boolean isSubscribed(ISourceDataTag tag);

}

