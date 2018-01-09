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
package cern.c2mon.daq.opcua.connection.common.impl;

import org.junit.Test;

import cern.c2mon.daq.opc.EndpointTypesUnknownException;
import cern.c2mon.daq.opc.common.IOPCEndpoint;
import cern.c2mon.daq.opc.common.impl.OPCDefaultAddress;
import cern.c2mon.daq.opcua.connection.ua.digitalpetri.UAEndpointDigitalpetri;

import static org.junit.Assert.assertEquals;

public class DefaultOPCEndpointFactoryTest {

    private DefaultOPCEndpointFactory factory = new DefaultOPCEndpointFactory();

    @Test
    public void testUAEndpointDefault() throws Exception {

        OPCDefaultAddress addr = new OPCDefaultAddress.DefaultBuilder(DefaultOPCEndpointFactory.UA_TCP_TYPE + "://test", 1232, 123).build();
        IOPCEndpoint endpoint = factory.createEndpoint(addr);

        assertEquals(endpoint.getClass(), UAEndpointDigitalpetri.class);
    }

    @Test(expected=EndpointTypesUnknownException.class)
    public void testNULLEndpoint() {
        factory.createEndpoint(null);
    }
}
