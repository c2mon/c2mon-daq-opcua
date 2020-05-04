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
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.util.Objects;

public class DataTagDefinition extends ItemDefinition {

    @Getter
    private final int timeDeadband;

    @Getter
    private final short valueDeadbandType;

    @Getter
    private final float valueDeadband;

    protected DataTagDefinition(final ISourceDataTag tag, final NodeId address, final NodeId redundantAddress) {
        super(address, redundantAddress);
        this.timeDeadband = tag.getTimeDeadband();
        this.valueDeadbandType = tag.getValueDeadbandType();
        this.valueDeadband = tag.getValueDeadband();
    }

    /**
     * The objects are considered equal if their tags have the same id.
     *
     * @param o The object to compare to.
     * @return true if the objects equal else false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        DataTagDefinition that = (DataTagDefinition) o;
        return super.equals(that) &&
                timeDeadband == that.timeDeadband &&
                valueDeadbandType == that.valueDeadbandType &&
                valueDeadband == that.valueDeadband;
    }

    /**
     * Returns the hash code of this object.
     *
     * @return The hash code of this object which equals the hash code of its
     * ID.
     */
    @Override
    public int hashCode() {
        return Objects.hash(timeDeadband, valueDeadbandType, valueDeadband);
    }
}
