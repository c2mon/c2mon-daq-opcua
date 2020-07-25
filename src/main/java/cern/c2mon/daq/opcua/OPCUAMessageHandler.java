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
import cern.c2mon.daq.common.conf.equipment.IEquipmentConfigurationChanger;
import cern.c2mon.daq.opcua.config.AddressParser;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.IControllerProxy;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.AliveWriter;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.DataTagChanger;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * The OPCUAMessageHandler is the entry point of the application. It is created and called by the C2MON DAQ core and
 * connects to exactly one OPC UA server. The handler can access pre-defined configurations from the C2MON server which
 * are provided by the core, subscribe to tags as defined in the configuration, handle equipment configuration updates,
 * and execute commands received by C2MON. The handler can also register listeners for optional events in this class.
 * @author Andreas Lang, Nacho Vilches
 */
@Slf4j
public class OPCUAMessageHandler extends EquipmentMessageHandler implements IEquipmentConfigurationChanger, ICommandRunner {

    private IControllerProxy controller;
    private IDataTagHandler dataTagHandler;
    private DataTagChanger dataTagChanger;
    private AliveWriter aliveWriter;
    private CommandTagHandler commandTagHandler;
    private AppConfigProperties appConfigProperties;

    /**
     * Called when the core wants the OPC UA module to start up. Connects to the OPC UA server, triggers initial
     * subscriptions of the tags given in the configuration, and subscribes listeners to update C2MON about changes in
     * equipment state and data values.
     * @throws EqIOException Throws an {@link EqIOException} if there is an IO problem during startup.
     */
    @Override
    public void connectToDataSource() throws EqIOException {
        controller = getContext().getBean("controller", IControllerProxy.class);
        dataTagHandler = getContext().getBean(IDataTagHandler.class);
        aliveWriter = getContext().getBean(AliveWriter.class);
        commandTagHandler = getContext().getBean(CommandTagHandler.class);
        appConfigProperties = getContext().getBean(AppConfigProperties.class);
        dataTagChanger = getContext().getBean(DataTagChanger.class);

        // getEquipmentConfiguration always fetches the most recent equipment configuration, even if changes have occurred to the configuration since start-up of the DAQ.
        IEquipmentConfiguration config = getEquipmentConfiguration();
        IEquipmentMessageSender sender = getEquipmentMessageSender();

        getContext().getBean(IMessageSender.class).initialize(sender);

        Collection<String> addresses = AddressParser.parse(config.getAddress(), appConfigProperties);
        log.info("Connecting to the OPC UA data source at {}... ", StringUtils.join(addresses, ", "));
            try {
                controller.connect(addresses);
            } catch (OPCUAException e) {
                sender.confirmEquipmentStateIncorrect(IMessageSender.EquipmentState.CONNECTION_FAILED.message);
                throw e;
            }
            dataTagHandler.subscribeTags(config.getSourceDataTags().values());
        aliveWriter.initialize(config, appConfigProperties.isAliveWriterEnabled());
        log.debug("connected");

        getEquipmentCommandHandler().setCommandRunner(this);
        getEquipmentConfigurationHandler().setDataTagChanger(dataTagChanger);
        getEquipmentConfigurationHandler().setCommandTagChanger(commandTagHandler);
        getEquipmentConfigurationHandler().setEquipmentConfigurationChanger(this);
    }

    /**
     * Called when the core wants the OPC module to disconnect from the OPC server and discard all configuration.
     */
    @Override
    public void disconnectFromDataSource() {
        log.debug("disconnecting from OPC data source...");
        dataTagHandler.reset();
        controller.stop();
        aliveWriter.stopWriter();
        log.debug("disconnected");
    }

    /** Triggers the refresh of all values directly from the OPC server. */
    @Override
    public void refreshAllDataTags() {
        dataTagHandler.refreshAllDataTags();
    }

    /**
     * Triggers the refresh of a single value directly from the OPC server.
     * @param dataTagId The id of the data tag to refresh.
     */
    @Override
    public void refreshDataTag(final long dataTagId) {
        ISourceDataTag sourceDataTag = getEquipmentConfiguration().getSourceDataTag(dataTagId);
        if (sourceDataTag == null) {
            log.error("SourceDataTag with ID {} is unknown", dataTagId);
        } else {
            dataTagHandler.refreshDataTag(sourceDataTag);
        }
    }

    /**
     * Executes a specific command.
     * @param value defines the command to run. The id of the value must correspond to the id of a command tag in the
     *              configuration.
     * @return If the command is of type method and returns values, the toString representation of the output is
     * returned. Otherwise null.
     * @throws EqCommandTagException thrown when the command cannot be executed, or the status is erroneous or
     *                               uncertain.
     */
    @Override
    public String runCommand(SourceCommandTagValue value) throws EqCommandTagException {
        Long id = value.getId();
        ISourceCommandTag tag = getEquipmentConfiguration().getSourceCommandTag(id);
        if (tag == null) {
            throw new EqCommandTagException("Command tag with id '" + id + "' unknown!");
        }
        if (log.isDebugEnabled()) {
            log.debug("running command {} with value {}", id, value.getValue());
        }
        return commandTagHandler.runCommand(tag, value);
    }

    /**
     * Apply changed to the equipment configuration
     * @param newConfig    The new equipment configuration.
     * @param oldConfig    A clone of the old equipment configuration.
     * @param changeReport Report object to fill.
     */
    @Override
    public void onUpdateEquipmentConfiguration(final IEquipmentConfiguration newConfig, final IEquipmentConfiguration oldConfig, final ChangeReport changeReport) {
        if (!newConfig.getAddress().equals(oldConfig.getAddress())) {
            try {
                synchronized (this) {
                    disconnectFromDataSource();
                    TimeUnit.MILLISECONDS.sleep(appConfigProperties.getRestartDelay());
                    connectToDataSource();
                }
                changeReport.appendInfo("DAQ restarted.");
            } catch (EqIOException e) {
                changeReport.appendError("Restart of DAQ failed.");
            } catch (InterruptedException e) {
                changeReport.appendError("Restart delay interrupted. DAQ will not connect.");
                Thread.currentThread().interrupt();
            }
        } else {
            if ((newConfig.getAliveTagId() != oldConfig.getAliveTagId()
                    || newConfig.getAliveTagInterval() != oldConfig.getAliveTagInterval())
                    && aliveWriter != null) {
                changeReport.appendInfo(aliveWriter.updateAndReport());
            }
            dataTagChanger.onUpdateEquipmentConfiguration(newConfig.getSourceDataTags().values(), oldConfig.getSourceDataTags().values(), changeReport);
        }
        changeReport.setState(CHANGE_STATE.SUCCESS);
    }
}