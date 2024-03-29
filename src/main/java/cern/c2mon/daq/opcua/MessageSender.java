/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
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
package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.AllArgsConstructor;

/**
 * Handles communication with the DAQ Core's {@link IEquipmentMessageSender}
 */
public interface MessageSender {

    /**
     * A representation of equipment states and descriptions.
     */
    @AllArgsConstructor
    enum EquipmentState {
        OK("Successfully connected"),
        CONNECTION_FAILED("Cannot establish connection to the server"),
        CONNECTION_LOST("Connection to server has been lost. Reconnecting...");
        /** A description of the state of the equipment and connection */
        public final String message;
    }

    /**
     * Initialize the EndpointListener with the IEquipmentMessageSender instance
     * @param sender the sender to notify of events
     */
    void initialize(IEquipmentMessageSender sender);

    /**
     * Updates the value of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} with the ID tagId
     * @param tagId the id of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} whose value to update
     * @param quality the {@link SourceDataTagQuality} of the updated value
     * @param valueUpdate the {@link ValueUpdate} to send to the {@link cern.c2mon.shared.common.datatag.ISourceDataTag}
     */
    void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate);

    /**
     * Notifies the {@link IEquipmentMessageSender} that a tagId could not be subscribed, or that a bad reading was obtained
     * @param tagId the id of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} to update to invalid
     * @param quality the quality of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag}
     */
    void onTagInvalid(long  tagId, final SourceDataTagQuality quality);

    /**
     * Send an update to the configured aliveTag
     */
    void onAlive();

    /**
     * Updates the state of equipment and connection.
     * @param state the state to update
     */
    void onEquipmentStateUpdate(EquipmentState state);

}
