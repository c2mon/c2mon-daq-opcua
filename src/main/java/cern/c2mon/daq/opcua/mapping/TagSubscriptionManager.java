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

/**
 * The {@link TagSubscriptionManager} allows the management of the subscription status of {@link
 * cern.c2mon.shared.common.datatag.ISourceDataTag}s via their associated {@link ItemDefinition}s and {@link
 * SubscriptionGroup}s on top of functionality granted by {@link TagSubscriptionReader}.
 */
public interface TagSubscriptionManager extends TagSubscriptionReader {

    /**
     * Registers the tagId as subscribed and adds the associated {@link ItemDefinition} to the appropriate {@link
     * SubscriptionGroup}.
     * @param tagId the tagId whose associated {@link ItemDefinition} to add to the appropriate {@link
     *              SubscriptionGroup}
     */
    void addTagToGroup(long tagId);

    /**
     * Registers the tagId as unsubscribed and removes the associated {@link ItemDefinition} from the corresponding
     * {@link SubscriptionGroup}.
     * @param tagId the tagId whose associated {@link ItemDefinition} to remove its {@link SubscriptionGroup}
     * @return true if the tagId was previously associated with an {@link ItemDefinition} which was part of a {@link
     * SubscriptionGroup}.
     */
    boolean removeTag(long tagId);

    /**
     * Removes all managed {@link ItemDefinition}s and {@link SubscriptionGroup}s from the internal state.
     */
    void clear();
}
