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


import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import cern.c2mon.shared.common.ConfigurationException;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;

public class AliveWriterTest {

  private OPCHardwareAddress hardwareAddress;
  private ISourceDataTag targetTag;
  private AliveWriter aliveWriter;

  private Endpoint endpointMock;


  @Before
  public void setUp() throws ConfigurationException {
    this.endpointMock = EasyMock.createMock(Endpoint.class);
    this.hardwareAddress = new OPCHardwareAddressImpl("s");
    DataTagAddress address = new DataTagAddress(hardwareAddress);
    this.targetTag = new SourceDataTag(1L, "asd", true, (short) 0, "Integer", address);
  }

  @Test
  public void testRun() {
    for (int k = 0; k < 3; k++) {
      for (int i = 0; i < Byte.MAX_VALUE; i++) {
        this.endpointMock.write(this.hardwareAddress, Integer.valueOf(i));
      }
    }

    EasyMock.replay(this.endpointMock);

    this.aliveWriter = new AliveWriter(this.endpointMock, 1000L, this.targetTag);


    for (int k = 0; k < 3; k++) {
      for (int i = 0; i < Byte.MAX_VALUE; i++) {
        this.aliveWriter.run();
      }
    }

    EasyMock.verify(this.endpointMock);
  }

}
