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
package cern.c2mon.daq.opcua;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import cern.c2mon.daq.common.logger.EquipmentLogger;
import cern.c2mon.daq.common.logger.EquipmentLoggerFactory;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;

public class EndpointEquipmentLogListenerTest {

    /**
     * Mocks
     */
    private EquipmentLogger equipmentLoggerMock;
    private EquipmentLoggerFactory equipmentLoggerFactoryMock;

    private EndpointEquipmentLogListener listener;


    @Before
    public void setUp() throws SecurityException, NoSuchMethodException {
//        equipmentLoggerMock = EasyMock.createMockBuilder(EquipmentLogger.class,
//                new ConstructorArgs(
//                        EquipmentLogger.class.getConstructor(
//                                String.class, String.class, String.class),
//                                "asd", "asd", "asd"),
//                EquipmentLogger.class.getMethod("isDebugEnabled"),
//                EquipmentLogger.class.getMethod("debug", Object.class));

        this.equipmentLoggerMock = EasyMock.createMockBuilder(EquipmentLogger.class).
            withConstructor(String.class, String.class, String.class).
            withArgs("asd", "asd", "EndpointEquipmentLogListener").
            addMockedMethod("isDebugEnabled").
//            addMockedMethod("debug", Object.class).
            createMock();
        this.equipmentLoggerFactoryMock = EasyMock.createMock(EquipmentLoggerFactory.class);
    }

    @Test
    public void testOnNewTagValue() {
        ISourceDataTag dataTag = new SourceDataTag(1L, "asd", false);
        Object value = "";
        long timestamp = 100L;

        EasyMock.expect(this.equipmentLoggerMock.isDebugEnabled()).andReturn(true);
        EasyMock.expect(this.equipmentLoggerFactoryMock.getEquipmentLogger(EndpointEquipmentLogListener.class))
            .andReturn(this.equipmentLoggerMock).times(1);

        EasyMock.replay(this.equipmentLoggerMock, this.equipmentLoggerFactoryMock);

        this.listener = new EndpointEquipmentLogListener(this.equipmentLoggerFactoryMock);

        this.equipmentLoggerMock.debug("New tag value (ID: '" + dataTag.getId() + "',"
                + " Value: '" + value + "', Timestamp: '" + timestamp + "').");

        this.listener.onNewTagValue(dataTag, timestamp, value);

        EasyMock.verify(this.equipmentLoggerMock, this.equipmentLoggerFactoryMock);
    }

    @Test
    public void testOnSubscriptionException() {
        Throwable cause = new Throwable();

        EasyMock.expect(this.equipmentLoggerFactoryMock.getEquipmentLogger(EndpointEquipmentLogListener.class))
            .andReturn(this.equipmentLoggerMock).times(1);

        this.equipmentLoggerMock.error("Exception in OPC subscription.", cause);

        EasyMock.replay(this.equipmentLoggerMock, this.equipmentLoggerFactoryMock);

        this.listener = new EndpointEquipmentLogListener(this.equipmentLoggerFactoryMock);

        this.listener.onSubscriptionException(cause);

        EasyMock.verify(this.equipmentLoggerMock, this.equipmentLoggerFactoryMock);
    }

    @Test
    public void testOnInvalidTagException() {
        Throwable cause = new Throwable();
        DataTagAddress address = new DataTagAddress(new OPCHardwareAddressImpl("asd"));
		SourceDataTag dataTag = new SourceDataTag(1L, "asd", false, (short) 0, "Boolean", address );

		EasyMock.expect(this.equipmentLoggerFactoryMock.getEquipmentLogger(EndpointEquipmentLogListener.class))
            .andReturn(this.equipmentLoggerMock).times(1);
        this.equipmentLoggerMock.warn("Tag with id '" + dataTag.getId() + "' caused exception. "
                + "Check configuration.", cause);

        EasyMock.replay(this.equipmentLoggerMock, this.equipmentLoggerFactoryMock);

        this.listener = new EndpointEquipmentLogListener(this.equipmentLoggerFactoryMock);
        this.listener.onTagInvalidException(dataTag, cause);

        EasyMock.verify(this.equipmentLoggerMock, this.equipmentLoggerFactoryMock);
    }

}