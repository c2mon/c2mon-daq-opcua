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
package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.AllArgsConstructor;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public interface EndpointListener {

    /**
     * A representation of equipment states and descriptions.
     */
    @AllArgsConstructor
    enum EquipmentState {
        OK("Successfully connected"),
        CONNECTION_FAILED("Cannot establish connection to the server"),
        CONNECTION_LOST("Connection to server has been lost. Reconnecting...");
        public final String message;
    }

    /**
     * Initialize the EndpointListener with the IEquipmentMessageSender instance
     * @param sender the sender to notify of events
     */
    void initialize(IEquipmentMessageSender sender);

    void onTagInvalid(final UInteger clientHandle, final SourceDataTagQuality quality);

    void onAlive();

    void onEquipmentStateUpdate(EquipmentState state);

    void onValueUpdate(UInteger clientHandle, SourceDataTagQualityCode quality, ValueUpdate valueUpdate);
}
