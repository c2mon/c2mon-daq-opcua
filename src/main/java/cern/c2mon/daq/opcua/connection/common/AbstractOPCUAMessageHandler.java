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
package cern.c2mon.daq.opcua.connection.common;

import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.opc.stack.connection.AbstractEquipmentMessageHandler;
import cern.c2mon.opc.stack.connection.common.impl.OPCCriticalException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;

/**
 * The AbstractOPCUAMessageHandler is the entry point of the application. It is created
 * and called by the core. Here the OPC module can access the configuration and
 * register listeners for optional events.
 * <p>
 * Abstract class (no implementation of this class)
 *
 * @author Nacho Vilches
 */
public abstract class AbstractOPCUAMessageHandler extends AbstractEquipmentMessageHandler {

  /**
   * Delay to restart the DAQ after an equipment change.
   */
  private static final long RESTART_DELAY = 2000L;

  /**
   * Called when the core wants the OPC module to start up and connect to the
   * OPC server.
   *
   * @throws EqIOException Throws an {@link EqIOException} if there is an IO
   *                       problem during startup.
   */
  @Override
  public abstract void connectToDataSource() throws EqIOException;

  /**
   * Called when the core wants the OPC module to disconnect from the OPC
   * server and discard all configuration.
   *
   * @throws EqIOException Throws an {@link EqIOException} if there is an IO
   *                       problem during stop.
   */
  @Override
  public synchronized void disconnectFromDataSource() throws EqIOException {
    getEquipmentLogger().debug("disconnecting from OPC data source...");
    controller.stop();
    getEquipmentLogger().debug("disconnected");
  }

  /**
   * Triggers the refresh of a single value directly from the OPC server.
   *
   * @param dataTagId The id of the data tag to refresh.
   */
  @Override
  public synchronized void refreshDataTag(final long dataTagId) {
    getEquipmentLogger().debug("refreshing data tag " + dataTagId);
    ISourceDataTag sourceDataTag =
            getEquipmentConfiguration().getSourceDataTag(dataTagId);
    if (sourceDataTag == null)
      throw new OPCCriticalException("SourceDataTag with id '" + dataTagId
              + "' unknown.");
    controller.refresh(sourceDataTag);
  }

  @Override
  public void shutdown() throws EqIOException {
    super.shutdown();
  }
}
