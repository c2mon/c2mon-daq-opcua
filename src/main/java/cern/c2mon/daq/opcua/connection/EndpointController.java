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

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.EndpointEquipmentLogListener;
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.EndpointTypesUnknownException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.config.ChangeReport;
import cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AbstractEndpointController abstract class (no implementation of this class)
 *
 * @author vilches
 */
@Slf4j
public class EndpointController implements IDataTagChanger {

    /**
     * The OPC UA TCP connection type.
     */
    public static final String UA_TCP_TYPE = "opc.tcp";

    /**
     * Properties for the opc endpoint.
     */
    protected List<EquipmentAddress> opcAddresses;


    /**
     * The currently controlled endpoint.
     */
    protected Endpoint endpoint;

    /**
     * The EquipmentMessageSender to send updates to.
     */
    protected IEquipmentMessageSender sender;

    /**
     * The alive writer to write to the OPC.
     */
    protected AliveWriter writer;

    /**
     * The equipment configuration for his controller.
     */
    protected IEquipmentConfiguration equipmentConfiguration;


    /**
     * Creates a new default EndpointController
     *
     * @param sender                 The equipment message sender to send updates to.
     * @param opcAddresses           The default addresses for the endpoints.
     * @param equipmentConfiguration The equipment configuration for this controller.
     */
    public EndpointController(final IEquipmentMessageSender sender,
                              final List<EquipmentAddress> opcAddresses,
                              final IEquipmentConfiguration equipmentConfiguration) {
        this.sender = sender;
        this.opcAddresses = opcAddresses;
        this.equipmentConfiguration = equipmentConfiguration;
    }

    /**
     * Starts this controllers endpoint for the first time. After this method is called the
     * controller will receive updates.
     *
     */
    public synchronized void startEndpoint() {
        try {
            startProcedure();
        } catch (OPCCommunicationException e) {
            log.error("Endpoint creation failed. Controller will try again. ", e);
            // Restart Endpoint
            triggerEndpointRestart("Problems connecting to " +  opcAddresses.stream().map(a -> a.getUri().getHost()).collect(Collectors.joining("")) + ": " + e.getMessage());
        }
    }

    /**
     * Restart the controller endpoint if the first attempt fails
     *
     * @return True if the reconnection was successful or false in any other case
     */
    private synchronized void restartEndpoint() {
        try {
            startProcedure();
        } catch (OPCCommunicationException e) {
            log.error("Endpoint creation failed. Controller will try again. ", e);
        }
    }

    /**
     * Procedure common to all starts (start and restart)
     */
    protected synchronized void startProcedure() throws OPCCommunicationException {

        // Create Endpoint
        initializeEndpoint();

        // Register Listeners
        this.endpoint.registerEndpointListener(new EndpointEquipmentLogListener());
        this.endpoint.registerEndpointListener(new EndpointListenerImpl(this.sender, this));

        // Add Tags to endpoint
        addTagsToEndpoint();

        // Send info message to Comm_fault tag
        this.sender.confirmEquipmentStateOK("Connected to " + endpoint.getEquipmentAddress().getUri().getHost());

        startAliveTimer();
    }

    /**
     * Add Data and Command tags to the the endpoint
     */
    protected void addTagsToEndpoint() {
        // Datatags
        this.endpoint.subscribeTags(this.equipmentConfiguration.getSourceDataTags().values());
    }

    /**
     * Starts the alive timer.
     */
    public synchronized void startAliveTimer() {
        ISourceDataTag targetTag = equipmentConfiguration.getSourceDataTag(
                equipmentConfiguration.getAliveTagId());
        boolean aliveWriterEnabled = endpoint.getEquipmentAddress().isAliveWriterEnabled();
        if (!aliveWriterEnabled) {
            log.info("Equipment Alive Timer has been disabled in the configuration ==> Alive Timer has not been started.");
        } else if (targetTag == null) {
            log.error("No equipment alive tag is specified. Check the configuration! ==> Alive Timer has not been started.");
        } else {
            writer = new AliveWriter(endpoint, equipmentConfiguration.getAliveTagInterval() / 2, targetTag);
            writer.startWriter();
        }
    }

    /**
     * Stops the alive timer.
     */
    public synchronized void stopAliveTimer() {
        if (writer != null) {
            writer.stopWriter();
        }
    }


    /**
     * Makes sure there is a created and initialized endpoint.
     */
    protected void initializeEndpoint() {
        if (this.endpoint != null) {
            this.endpoint.reset();
        }
        Optional<EquipmentAddress> matchingAddress = this.opcAddresses.stream().filter(o -> o.getProtocol().equals(UA_TCP_TYPE)).findFirst();
        if (!matchingAddress.isPresent()) {
            log.error("No supported protocol found. createEndpoint - Endpoint creation for '" + this.opcAddresses + "' failed. Stop Startup.");
            throw new EndpointTypesUnknownException();
        }
        this.endpoint = new EndpointImpl(new TagSubscriptionMapperImpl());
        this.endpoint.initialize(matchingAddress.get());
    }

    /**
     * Stops this endpoint and goes back to the state before the start method
     * was called.
     */
    public synchronized void stop() {
        stopAliveTimer();
        if (endpoint != null)
            endpoint.reset();
    }

    /**
     * Refreshes the values of all added data tags.
     */
    public synchronized void refresh() {
        log.info("refresh - Refreshing values of all data tags.");
        isEndpointConnected();
        this.endpoint.refreshDataTags(this.equipmentConfiguration.getSourceDataTags().values());
    }

    /**
     * Refreshes the values of the provided source data tag.
     *
     * @param sourceDataTag The source data tag to refresh the value for.
     */
    public synchronized void refresh(final ISourceDataTag sourceDataTag) {
        isEndpointConnected();
        Collection<ISourceDataTag> tags = new ArrayList<ISourceDataTag>(1);
        tags.add(sourceDataTag);
        log.info("Refreshing value of data tag with id '" + sourceDataTag.getId() + "'.");
        this.endpoint.refreshDataTags(tags);
    }



    /**
     * Triggers the restart of this endpoint.
     *
     * @param reason The reason of the restart, if any applicable.
     */
    public synchronized void triggerEndpointRestart(final String reason) {
        // Do while the endpoint state changes to OPERATOINAL
        do {
            // Stop endpoint and send message to COMM_FAULT tag
            try {
                EndpointController.this.stop();
            } catch (Exception ex) {
                log.warn("triggerEndpointRestart - Error stopping endpoint subscription for " +
                        endpoint.getEquipmentAddress().getUri().getHost(), ex);
            } finally {
                if (reason == null || reason.equalsIgnoreCase("")) {
                    sender.confirmEquipmentStateIncorrect();
                } else {
                    sender.confirmEquipmentStateIncorrect(reason);
                }
            }

            // Sleep before retrying
            try {
                if (log.isDebugEnabled()) {
                    log.debug("triggerEndpointRestart - Server " + endpoint.getEquipmentAddress().getUri().getHost()
                            + " - Sleeping for " + endpoint.getEquipmentAddress().getServerRetryTimeout() + " ms ...");
                }
                Thread.sleep(endpoint.getEquipmentAddress().getServerRetryTimeout());
            } catch (InterruptedException e) {
                log.error("Subscription restart interrupted for " + endpoint.getEquipmentAddress().getUri().getHost(), e);
            }

            // Retry
            try {
                restartEndpoint();
                refresh();
            } catch (Exception e) {
                log.error("Error restarting subscription for " + endpoint.getEquipmentAddress().getUri().getHost(), e);
            }
        } while (!endpoint.isConnected());
        log.info("triggerEndpointRestart - Exiting OPC Endpoint restart procedure for " + endpoint.getEquipmentAddress().getUri().getHost());
    }


    /**
     * Adds a data tag.
     *
     * @param sourceDataTag The data tag to add.
     * @param changeReport  The change report object which needs to be filled.
     */
    @Override
    public void onAddDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        log.info("Adding data tag " + sourceDataTag.getId());
        isEndpointConnected();
        this.endpoint.subscribeTag(sourceDataTag);
        refresh(sourceDataTag);
        changeReport.appendInfo("DataTag added.");
        changeReport.setState(CHANGE_STATE.SUCCESS);
        log.info("Added data tag " + sourceDataTag.getId());
    }

    /**
     * Removes a data tag.
     *
     * @param sourceDataTag The data tag to remove.
     * @param changeReport  The change report object which needs to be filled.
     */
    @Override
    public void onRemoveDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        log.info("Removing data tag " + sourceDataTag.getId());
        isEndpointConnected();
        this.endpoint.removeDataTag(sourceDataTag);
        changeReport.appendInfo("DataTag removed.");
        changeReport.setState(CHANGE_STATE.SUCCESS);
        log.info("Removed data tag " + sourceDataTag.getId());
    }

    /**
     * Updates a data tag.
     *
     * @param sourceDataTag    The data tag to update.
     * @param oldSourceDataTag A copy of the former state of the data tag.
     * @param changeReport     The change report object which needs to be filled.
     */
    @Override
    public void onUpdateDataTag(final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        log.info("Updating data tag " + sourceDataTag.getId());
        if (!isEndpointConnected()) {
            initializeEndpoint();
        }
        if (!sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            this.endpoint.removeDataTag(oldSourceDataTag);
            this.endpoint.subscribeTag(sourceDataTag);
            changeReport.appendInfo("Data tag updated.");
        } else {
            changeReport.appendInfo("No changes for OPC necessary.");
        }
        changeReport.setState(CHANGE_STATE.SUCCESS);
        log.info("Updated data tag " + sourceDataTag.getId());
    }

    private boolean isEndpointConnected() {
        return (this.endpoint != null && this.endpoint.isConnected());
    }

    /**
     * @return the writer
     */
    public AliveWriter getWriter() {
        return writer;
    }
}
