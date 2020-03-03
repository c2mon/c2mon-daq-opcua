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

import cern.c2mon.daq.common.EquipmentMessageHandler;
import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.common.conf.equipment.IEquipmentConfigurationChanger;
import cern.c2mon.daq.opcua.connection.Controller;
import cern.c2mon.daq.opcua.connection.ControllerFactory;
import cern.c2mon.daq.opcua.exceptions.AddressException;
import cern.c2mon.daq.opcua.exceptions.EndpointTypesUnknownException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.daq.opcua.upstream.EndpointListenerImpl;
import cern.c2mon.daq.opcua.upstream.EquipmentStateListener;
import cern.c2mon.daq.opcua.upstream.TagListener;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.config.ChangeReport;
import cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OPCUAMessageHandler extends EquipmentMessageHandler implements IEquipmentConfigurationChanger {

  /**
   * Delay to restart the DAQ after an equipment change.
   */
  private static final long RESTART_DELAY = 2000L;

  @Getter
  private Controller controller;

  /**
   * Called when the core wants the OPC module to start up and connect to the
   * OPC server.
   *
   * @throws EqIOException Throws an {@link EqIOException} if there is an IO
   *                       problem during startup.
   */
  @Override
  public synchronized void connectToDataSource () throws EqIOException {
    IEquipmentConfiguration config = getEquipmentConfiguration();
    IEquipmentMessageSender sender = getEquipmentMessageSender();

    try {
      controller = ControllerFactory.getController(config);
      subscribeEndpointListeners(sender);
      controller.initialize();
    } catch (AddressException e) {
      throw new EqIOException("OPC address configuration string is invalid.", e);
    } catch (EndpointTypesUnknownException e) {
      throw new EqIOException("Not an appropriate OPC-UA protocol.", e);
    }

    getEquipmentConfigurationHandler().setDataTagChanger((IDataTagChanger) controller);
    getEquipmentConfigurationHandler().setEquipmentConfigurationChanger(this);
  }

  private void subscribeEndpointListeners(IEquipmentMessageSender sender) {
    EndpointListenerImpl listener = new EndpointListenerImpl(sender);
    controller.subscribe((TagListener) listener);
    controller.subscribe((EquipmentStateListener) listener);
  }

  /**
   * Called when the core wants the OPC module to disconnect from the OPC
   * server and discard all configuration.
   */
  @Override
  public synchronized void disconnectFromDataSource () {
    log.debug("disconnecting from OPC data source...");
    controller.stop();
    log.debug("disconnected");
  }

  /**
   * Triggers the refresh of all values directly from the OPC server.
   */
  @Override
  public synchronized void refreshAllDataTags () {
    new Thread(() -> {
        controller.refreshAllDataTags();
    }).start();
  }

  /**
   * Triggers the refresh of a single value directly from the OPC server.
   *
   * @param dataTagId The id of the data tag to refresh.
   */
  @Override
  public synchronized void refreshDataTag (final long dataTagId) {
    ISourceDataTag sourceDataTag = getEquipmentConfiguration().getSourceDataTag(dataTagId);
    if (sourceDataTag == null) {
      throw new OPCCriticalException("SourceDataTag with id '" + dataTagId + "' unknown.");
    }
    controller.refreshDataTag(sourceDataTag);
  }


  /**
   * Makes sure the changes to the equipment are applied on OPC level.
   *
   * @param equipmentConfiguration    The new equipment configuration.
   * @param oldEquipmentConfiguration A clone of the old equipment configuration.
   * @param changeReport              Report object to fill.
   */
  @Override
  public synchronized void onUpdateEquipmentConfiguration (
          final IEquipmentConfiguration equipmentConfiguration,
          final IEquipmentConfiguration oldEquipmentConfiguration,
          final ChangeReport changeReport) {
    if (equipmentConfiguration.getAddress().equals(oldEquipmentConfiguration.getAddress())) {
      try {
        disconnectFromDataSource();
        Thread.sleep(RESTART_DELAY);
        connectToDataSource();
        changeReport.appendInfo("DAQ restarted.");
      } catch (EqIOException e) {
        changeReport.appendError("Restart of DAQ failed.");
      } catch (InterruptedException e) {
        changeReport.appendError("Restart delay interrupted. DAQ will not connect.");
      }
    } else if (equipmentConfiguration.getAliveTagId() != oldEquipmentConfiguration.getAliveTagId()
            || equipmentConfiguration.getAliveTagInterval() != oldEquipmentConfiguration.getAliveTagInterval()) {
      changeReport.appendInfo(controller.updateAliveWriterAndReport());
    }
    changeReport.setState(CHANGE_STATE.SUCCESS);
  }

  @Override
  public void shutdown () throws EqIOException {
    super.shutdown();
  }
}
