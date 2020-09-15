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
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.*;

public class OPCUAMessageSenderTest {

    IEquipmentMessageSender eqSenderMock = niceMock(IEquipmentMessageSender.class);
    OPCUAMessageSender sender = new OPCUAMessageSender();

    @BeforeEach
    public void setUp() {
        sender.initialize(eqSenderMock);
    }

    @Test
    public void onValueUpdateShouldUpdateWithValueIfQualityIsValid() {
        final SourceDataTagQuality valid = new SourceDataTagQuality(SourceDataTagQualityCode.OK);
        ValueUpdate valueUpdate = new ValueUpdate(null);
        eqSenderMock.update(0L, valueUpdate, valid);
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onValueUpdate(0L, valid, valueUpdate);
        verify(eqSenderMock);
    }


    @Test
    public void onValueUpdateShouldUpdateWithoutValueIfQualityIsValid() {
        final SourceDataTagQuality invalid = new SourceDataTagQuality(SourceDataTagQualityCode.CONVERSION_ERROR);
        eqSenderMock.update(0L, invalid);
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onValueUpdate(0L, invalid, null);
        verify(eqSenderMock);
    }

    @Test
    public void onAliveShouldNotifySender() {
        eqSenderMock.sendSupervisionAlive();
        expectLastCall().once();
        replay(eqSenderMock);
        sender.onAlive();
        verify(eqSenderMock);
    }

    @Test
    public void onEquipmentStateUpdateShouldSendOKIfStateOK() {
        final MessageSender.EquipmentState state = MessageSender.EquipmentState.OK;
        eqSenderMock.confirmEquipmentStateOK(anyString());
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onEquipmentStateUpdate(state);
        verify(eqSenderMock);
    }



    @Test
    public void onEquipmentStateUpdateShouldSendOKIfStringIsOk() {
        eqSenderMock.confirmEquipmentStateOK(anyString());
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onEquipmentStateUpdate("ok");
        verify(eqSenderMock);
    }


    @Test
    public void onEquipmentStateUpdateShouldSendIncorrectOnOtherString() {
        eqSenderMock.confirmEquipmentStateIncorrect(anyString());
        expectLastCall().times(1);
        replay(eqSenderMock);

        sender.onEquipmentStateUpdate("Bad");
        verify(eqSenderMock);
    }

    @Test
    public void onEquipmentStateUpdateShouldSendIncorrectOnOtherState() {
        eqSenderMock.confirmEquipmentStateIncorrect(anyString());
        expectLastCall().times(2);
        replay(eqSenderMock);

        sender.onEquipmentStateUpdate(MessageSender.EquipmentState.CONNECTION_LOST);
        sender.onEquipmentStateUpdate(MessageSender.EquipmentState.CONNECTION_LOST);
        verify(eqSenderMock);
    }

}
