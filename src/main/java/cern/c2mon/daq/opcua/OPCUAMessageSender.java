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
package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * Handles communication with the DAQ Core's {@link IEquipmentMessageSender}
 */
@NoArgsConstructor
@Slf4j
@Primary
@ManagedResource(objectName = "OPCUAMessageSender", description = "Communicate with the DAQ Core process")
@EquipmentScoped
public class OPCUAMessageSender implements MessageSender {

    private IEquipmentMessageSender sender;

    @Override
    public void initialize(IEquipmentMessageSender sender) {
        this.sender = sender;
    }

    @Override
    public void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
        if (quality.isValid()) {
            this.sender.update(tagId, valueUpdate, quality);
        } else {
            onTagInvalid(tagId, quality);
        }
    }

    @Override
    public void onTagInvalid(long tagId, final SourceDataTagQuality quality) {
        this.sender.update(tagId, quality);
    }

    @Override
    @ManagedOperation(description = "Manually trigger an AliveTag to be sent.")
    public void onAlive() {
        sender.sendSupervisionAlive();
    }

    @Override
    public void onEquipmentStateUpdate(EquipmentState state) {
        if (state == EquipmentState.OK) {
            sender.confirmEquipmentStateOK(state.message);
        } else {
            sender.confirmEquipmentStateIncorrect(state.message);
        }
    }

    /**
     * Send a CommfaultTag update as an actuator operation.
     * @param state The EquipmentState to send with the Commfault. The value 'OK' will send set the CommfaultTag to
     *              false, any other value will set it to true.
     */
    @ManagedOperation(description = "Manually trigger a CommfaultTag to be sent. The value 'OK' will send set the CommfaultTag to false, any other value will set it to true.")
    public void onEquipmentStateUpdate(String state) {
        if (state.equalsIgnoreCase(EquipmentState.OK.name())) {
            sender.confirmEquipmentStateOK(state);
        } else {
            sender.confirmEquipmentStateIncorrect(state);
        }
    }
}
