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
import lombok.Getter;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.HashSet;
import java.util.Set;

@Getter
public class SubscriptionGroup{

    private final Set<Long> tagIds = new HashSet<>();

    @Setter
    private UaSubscription subscription;

    private final int publishInterval;

    public SubscriptionGroup(int publishInterval) {
        this.publishInterval = publishInterval;
    }

    public int size() {
        return this.tagIds.size();
    }

    public boolean isSubscribed() {
        return this.subscription != null;
    }

    public void add(final long tagId) {
        this.tagIds.add(tagId);
    }

    public void reset() {
        tagIds.clear();
        subscription = null;
    }

    public boolean remove(final ISourceDataTag tag) {
        return this.tagIds.remove(tag.getId());
    }

    public boolean contains(ISourceDataTag tag) {
        return tagIds.contains(tag.getId());
    }
}