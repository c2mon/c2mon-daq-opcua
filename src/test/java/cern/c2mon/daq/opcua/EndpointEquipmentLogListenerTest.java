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

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import cern.c2mon.daq.opc.EndpointEquipmentLogListener;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;

@Slf4j
public class EndpointEquipmentLogListenerTest {

    private EndpointEquipmentLogListener listener;

    @Test
    public void testOnNewTagValue() {
        ISourceDataTag dataTag = new SourceDataTag(1L, "asd", false);
        Object value = "";
        long timestamp = 100L;

        this.listener = new EndpointEquipmentLogListener();

        log.debug("New tag value (ID: '" + dataTag.getId() + "',"
                + " Value: '" + value + "', Timestamp: '" + timestamp + "').");

        this.listener.onNewTagValue(dataTag, timestamp, value);
    }

    @Test
    public void testOnSubscriptionException() {
        Throwable cause = new Throwable();

        this.listener = new EndpointEquipmentLogListener();

        this.listener.onSubscriptionException(cause);
    }

    @Test
    public void testOnInvalidTagException() {
        Throwable cause = new Throwable();
        DataTagAddress address = new DataTagAddress(new OPCHardwareAddressImpl("asd"));
        SourceDataTag dataTag = new SourceDataTag(1L, "asd", false, (short) 0, "Boolean", address );

        this.listener = new EndpointEquipmentLogListener();
        this.listener.onTagInvalidException(dataTag, cause);
    }

}
