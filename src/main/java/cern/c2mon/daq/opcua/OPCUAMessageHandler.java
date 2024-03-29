/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
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

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_FAILED;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;

import cern.c2mon.daq.common.EquipmentMessageHandler;
import cern.c2mon.daq.common.ICommandRunner;
import cern.c2mon.daq.common.conf.core.ProcessConfigurationHolder;
import cern.c2mon.daq.common.conf.equipment.IEquipmentConfigurationChanger;
import cern.c2mon.daq.opcua.config.AddressParser;
import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.scope.EquipmentScope;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import cern.c2mon.daq.opcua.taghandling.AliveWriter;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.DataTagChanger;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.common.process.ProcessConfiguration;
import cern.c2mon.shared.common.process.SubEquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE;
import lombok.extern.slf4j.Slf4j;

/**
 * The OPCUAMessageHandler is the entry point of the application. It is created and called by the C2MON DAQ core and
 * connects to exactly one OPC UA server. The handler can access pre-defined configurations from the C2MON server which
 * are provided by the core, subscribe to tags as defined in the configuration, handle equipment configuration updates,
 * and execute commands received by C2MON. The handler can also register listeners for optional events in this class.
 * @author Andreas Lang, Nacho Vilches
 */
@Slf4j
public class OPCUAMessageHandler extends EquipmentMessageHandler implements IEquipmentConfigurationChanger, ICommandRunner {

    private Controller controller;
    private IDataTagHandler dataTagHandler;
    private DataTagChanger dataTagChanger;
    private AliveWriter aliveWriter;
    private CommandTagHandler commandTagHandler;
    private AppConfigProperties appConfigProperties;
    private MessageSender sender;
    private EquipmentScope scope;


    /**
     * Called when the core wants the OPC UA module to start up. Connects to the OPC UA server, triggers initial
     * subscriptions of the tags given in the configuration, and subscribes listeners to update C2MON about changes in
     * equipment state and data values.
     * @throws EqIOException Throws an {@link EqIOException} if there is an IO problem during startup.
     */
    @Override
    public void connectToDataSource() throws EqIOException {
        log.info("Initializing the OPC UA DAQ");
        IEquipmentConfiguration config = getEquipmentConfiguration();

        if (scope == null) {
            initializeScope(config.getName(), config.getId());
        }

        log.info("Connecting to the OPC UA data source at {}... ", config.getAddress());
        Collection<String> addresses = AddressParser.parse(config.getAddress(), appConfigProperties);
        try {
            controller.connect(addresses);
        } catch (OPCUAException e) {
            log.error("Connection failed with error: ", e);
            sender.onEquipmentStateUpdate(CONNECTION_FAILED);
            throw e;
        }

        log.info("Subscribing to Tags...");
        dataTagHandler.subscribeTags(config.getSourceDataTags().values());

        if (appConfigProperties.isAliveWriterEnabled()) {
            try {
                startAliveWriter(config);
            } catch (ConfigurationException e) {
                log.info("Proceeding without starting the affected AliveWriters. ", e);
            }
        } else {
            log.info("Alive Writer is disabled.");
        }

        getEquipmentCommandHandler().setCommandRunner(this);
        getEquipmentConfigurationHandler().setDataTagChanger(dataTagChanger);
        getEquipmentConfigurationHandler().setCommandTagChanger(commandTagHandler);
        getEquipmentConfigurationHandler().setEquipmentConfigurationChanger(this);
        log.info("OPC UA DAQ was initialized successfully!");
    }

    /**
     * Called when the core wants the OPC module to disconnect from the OPC server and discard all configuration.
     */
    @Override
    public void disconnectFromDataSource() {
        log.info("Disconnecting from OPC data source...");
        dataTagHandler.reset();
        controller.stop();
        aliveWriter.stopAliveWriter();
        log.info("Disconnected");
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
            log.error("SourceDataTag with ID {} is unknown.", dataTagId);
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
            log.error("SourceCommandTagValue with ID {} is unknown.", id);
            throw new EqCommandTagException("Command tag with id '" + id + "' unknown!");
        }
        log.info("Running command {} with value {}", id, value.getValue());
        return commandTagHandler.runCommand(tag, value);
    }

    /**
     * Apply a changed equipment configuration to the DAQ. If there is a change to the equipment address, the DAQ is
     * restarted. If the changes are restricted to the AliveTag ID or interval, the changes are applied to the running
     * DAQ.
     * @param newConfig    The new equipment configuration.
     * @param oldConfig    A clone of the old equipment configuration.
     * @param changeReport A report of changes and success state. A change is only considered to have failed if a
     *                     restart of the DAQ failed.
     */
    @Override
    public void onUpdateEquipmentConfiguration(final IEquipmentConfiguration newConfig, final IEquipmentConfiguration oldConfig, final ChangeReport changeReport) {
        log.info("Updating the Equipment Configuration");
        changeReport.setState(CHANGE_STATE.PENDING);
        if (!newConfig.getAddress().equals(oldConfig.getAddress())) {
            restartDAQ(changeReport);
        } else {
            log.info("Resubscribing DataTags");
            dataTagChanger.onUpdateEquipmentConfiguration(newConfig.getSourceDataTags().values(), oldConfig.getSourceDataTags().values(), changeReport);
            if ((newConfig.getAliveTagId() != oldConfig.getAliveTagId() || newConfig.getAliveTagInterval() != oldConfig.getAliveTagInterval())
                    && appConfigProperties.isAliveWriterEnabled()) {
                try {
                    startAliveWriter(newConfig);
                    changeReport.appendInfo("Alive Writer updated.");
                } catch (ConfigurationException e) {
                    log.error("Updating the Alive Writer failed.", e);
                    changeReport.appendError(e.getMessage());
                }
            }
            changeReport.setState(CHANGE_STATE.SUCCESS);
        }
        log.info("Finished the Equipment Configuration update!");
    }

    /**
     * {@link EquipmentScoped} beans should be shared across one instance of the OPCUAMessageHandler. Registering a new
     * instance of the prototype-scoped {@link EquipmentScope} once when initiating the data collection process ensures
     * that a new set of beans is created.
     */
    private void initializeScope(String equipmentName, Long equipmentId) {
        ApplicationContext ctx = getContext();
        scope = new EquipmentScope();
        ((ConfigurableBeanFactory) ctx.getAutowireCapableBeanFactory()).registerScope("equipment", scope);
        scope.setExporter(ctx.getBean(AppConfig.class).mBeanExporter());
        ProcessConfiguration processConfiguration = ProcessConfigurationHolder.getInstance();
        scope.initialize(equipmentName, equipmentId, processConfiguration.getProcessName(), processConfiguration.getProcessID(), ctx);
        controller = ctx.getBean(Controller.class);
        dataTagHandler = ctx.getBean(IDataTagHandler.class);
        commandTagHandler = ctx.getBean(CommandTagHandler.class);
        appConfigProperties = ctx.getBean(AppConfigProperties.class);
        dataTagChanger = ctx.getBean(DataTagChanger.class);
        aliveWriter = ctx.getBean(AliveWriter.class);
        sender = ctx.getBean(MessageSender.class);
        sender.initialize(getEquipmentMessageSender());
    }

    private void startAliveWriter(IEquipmentConfiguration config) throws ConfigurationException {
        StringBuilder errorLog = new StringBuilder(startAliveWriter(config.getAliveTagId(), config.getAliveTagInterval(), config));
        for (SubEquipmentConfiguration c : config.getSubEquipmentConfigurations().values()) {
            String s = startAliveWriter(c.getAliveTagId(), c.getAliveInterval(), config);
            errorLog.append(s.isEmpty() ? s : "\n " + s);
        }
        if (errorLog.length() > 0) {
            throw new ConfigurationException(ExceptionContext.ALIVE_WRITER_FAIL, errorLog.toString());
        }
    }

    private String startAliveWriter(Long aliveTagId, Long aliveTagInterval, IEquipmentConfiguration config) {
        String idLog = "Could not start supervision for aliveTag with ID " + aliveTagId + ": ";
        if (aliveTagInterval == 0) {
            return idLog + ExceptionContext.BAD_ALIVE_TAG_INTERVAL.getMessage();
        }
        ISourceDataTag aliveTag = config.getSourceDataTag(aliveTagId);
        if (aliveTag == null) {
            return idLog + ExceptionContext.BAD_ALIVE_TAG.getMessage();
        }
        aliveWriter.startAliveWriter(aliveTag, aliveTagInterval);
        return "";
    }

    private void restartDAQ(final ChangeReport changeReport) {
        log.info("Restarting the DAQ...");
        try {
            changeReport.setState(CHANGE_STATE.REBOOT);
            synchronized (this) {
                disconnectFromDataSource();
                TimeUnit.MILLISECONDS.sleep(appConfigProperties.getRestartDelay());
                connectToDataSource();
            }
            changeReport.appendInfo("DAQ restarted.");
            changeReport.setState(CHANGE_STATE.SUCCESS);
        } catch (EqIOException e) {
            log.error("Restart of the DAQ failed: ", e);
            changeReport.setState(CHANGE_STATE.FAIL);
            changeReport.appendError("Restart of DAQ failed.");
        } catch (InterruptedException e) {
            log.error("Restart of the DAQ was interrupted.: ", e);
            changeReport.appendError("Restart delay interrupted. DAQ will not connect.");
            Thread.currentThread().interrupt();
            changeReport.setState(CHANGE_STATE.FAIL);
        }
    }
}
