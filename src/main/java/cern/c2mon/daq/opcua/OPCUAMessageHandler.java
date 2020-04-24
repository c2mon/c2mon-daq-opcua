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
import cern.c2mon.daq.common.ICommandRunner;
import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.common.conf.equipment.IEquipmentConfigurationChanger;
import cern.c2mon.daq.opcua.connection.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EndpointListenerImpl;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.concurrent.TimeUnit;

/**
 * The OPCUAMessageHandler is the entry point of the application. It is created and called by the C2MON DAQ core and
 * connects to exactly one OPC UA server. The handler can access pre-defined configurations from the C2MON server which
 * are provided by the core, subscribe to tags as defined in the configuration, handle equipment configuration updates,
 * and execute commands received by C2MON. NOTE that currently, the module supports only write commands, not yet
 * server methods.
 * The handler can also register listeners for optional events in this class.
 *
 * @author Andreas Lang, Nacho Vilches
 */

@Slf4j
@EnableAutoConfiguration
public class OPCUAMessageHandler extends EquipmentMessageHandler implements IEquipmentConfigurationChanger, ICommandRunner {
  /**
   * Delay to restart the DAQ after an equipment change.
   */
  private static final long RESTART_DELAY = 2000L;

  @Getter
  private Controller controller;

  @Setter
  private EndpointListener endpointListener = new EndpointListenerImpl();

  @Autowired
  public void setController(Controller controller) {
    this.controller = controller;
  }

  /**
   * Called when the core wants the OPC UA module to start up. Connects to the OPC UA server, triggers initial
   * subscriptions of the tags given in the configuration, and subscribes listeners to update C2MON about changes in
   * equipment state and data values.
   *
   * @throws EqIOException Throws an {@link EqIOException} if there is an IO problem during startup.
   */
  @Override
  public synchronized void connectToDataSource() throws EqIOException {
    IEquipmentConfiguration config = getEquipmentConfiguration();
    IEquipmentMessageSender sender = getEquipmentMessageSender();

    log.debug("connect to the OPC UA data source...");
    endpointListener.initialize(sender);
    controller.subscribe(endpointListener);
    controller.initialize(config);
    log.debug("connected");

    getEquipmentConfigurationHandler().setDataTagChanger((IDataTagChanger) controller);
    getEquipmentConfigurationHandler().setEquipmentConfigurationChanger(this);
  }


  /**
   * Called when the core wants the OPC module to disconnect from the OPC
   * server and discard all configuration.
   */
  @Override
  public synchronized void disconnectFromDataSource() {
    log.debug("disconnecting from OPC data source...");
    if (controller != null) controller.stop();
    log.debug("disconnected");
  }

  /**
   * Triggers the refresh of all values directly from the OPC server.
   */
  @Override
  public synchronized void refreshAllDataTags() {
    controller.refreshAllDataTags();
  }

  /**
   * Triggers the refresh of a single value directly from the OPC server.
   *
   * @param dataTagId The id of the data tag to refresh.
   */
  @Override
  public synchronized void refreshDataTag(final long dataTagId) {
    ISourceDataTag sourceDataTag = getEquipmentConfiguration().getSourceDataTag(dataTagId);
    if (sourceDataTag == null) {
      throw new OPCCriticalException("SourceDataTag with id '" + dataTagId + "' unknown.");
    }
    controller.refreshDataTag(sourceDataTag);
  }

  /**
   * Executes a specific command.
   * @param value defines the command to run. The id of the value must correspond to the id of a command tag in the
   *              configuration.
   * @return null
   * @throws EqCommandTagException thrown when the command cannot be executed.
   */
  @Override
  public String runCommand(SourceCommandTagValue value) throws EqCommandTagException {
    Long id = value.getId();
    ISourceCommandTag tag = getEquipmentConfiguration().getSourceCommandTag(id);
    if (tag == null) {
      throw new EqCommandTagException("Command tag with id '" + id + "' unknown!");
    }
    try {
      if (log.isDebugEnabled()) {
        log.debug("running command {} with value {}", id, value.getValue());
      }
      controller.runCommand(tag, value);
    } catch (OPCCommunicationException e) {
      throw new EqCommandTagException("The client could not write to the specified hardware address", e);
    } catch (ConfigurationException e) {
      throw new EqCommandTagException("Please check whether the configuration is correct.", e);
    }
    return null;

  }

  /**
   * Makes sure the changes to the equipment are applied on OPC level.
   *
   * @param equipmentConfiguration    The new equipment configuration.
   * @param oldEquipmentConfiguration A clone of the old equipment configuration.
   * @param changeReport              Report object to fill.
   */
  @Override
  public synchronized void onUpdateEquipmentConfiguration(
          final IEquipmentConfiguration equipmentConfiguration,
          final IEquipmentConfiguration oldEquipmentConfiguration,
          final ChangeReport changeReport) {
    if (equipmentConfiguration.getAddress().equals(oldEquipmentConfiguration.getAddress())) {
      try {
        disconnectFromDataSource();
        TimeUnit.MILLISECONDS.sleep(RESTART_DELAY);
        connectToDataSource();
        changeReport.appendInfo("DAQ restarted.");
      } catch (EqIOException e) {
        changeReport.appendError("Restart of DAQ failed.");
      } catch (InterruptedException e) {
        changeReport.appendError("Restart delay interrupted. DAQ will not connect.");
        Thread.currentThread().interrupt();
      }
    } else if (equipmentConfiguration.getAliveTagId() != oldEquipmentConfiguration.getAliveTagId()
            || equipmentConfiguration.getAliveTagInterval() != oldEquipmentConfiguration.getAliveTagInterval()) {
      changeReport.appendInfo(controller.updateAliveWriterAndReport());
    }
    changeReport.setState(CHANGE_STATE.SUCCESS);
  }
}