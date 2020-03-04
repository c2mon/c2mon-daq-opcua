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
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EquipmentStateListener;
import cern.c2mon.daq.opcua.upstream.TagListener;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Slf4j
@AllArgsConstructor
public class ControllerImpl implements Controller, IDataTagChanger {

    @Getter
    private Endpoint endpoint;
    private IEquipmentConfiguration config;
    private EventPublisher publisher;
    private IEquipmentMessageSender sender;

    public void initialize () throws ConfigurationException {
        initialize(false);
    }

    public void initialize (boolean connectionLost) throws ConfigurationException {
        EndpointListener endpointListener = new EndpointListener(sender);
        publisher.subscribe((TagListener) endpointListener);
        publisher.subscribe((EquipmentStateListener) endpointListener);
        endpoint.initialize(connectionLost);
        endpoint.subscribeTags(config.getSourceDataTags().values());
    }

    public synchronized void stop () {
        endpoint.reset();
    }

    public void checkConnection () throws OPCCommunicationException, ConfigurationException {
        if (!endpoint.isConnected()) {
            endpoint.reset();
            initialize(true);
            refreshAllDataTags();
        }
    }

    public synchronized void refreshAllDataTags () {
        endpoint.refreshDataTags(config.getSourceDataTags().values());
    }

    public synchronized void refreshDataTag (ISourceDataTag sourceDataTag) {
        endpoint.refreshDataTags(Collections.singletonList(sourceDataTag));
    }

    public String updateAliveWriterAndReport () {
        return  "Alive Writer is not active -> not updated.";
    }

    @Override
    public void onAddDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        doAndReport(changeReport, "DataTag added", () -> {
            endpoint.subscribeTag(sourceDataTag);
            refreshDataTag(sourceDataTag);
        });
    }

    @Override
    public void onRemoveDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        doAndReport(changeReport, "DataTag removed", () -> endpoint.removeDataTag(sourceDataTag));
    }

    @Override
    public void onUpdateDataTag (final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            completeSuccessFullyWithMessage(changeReport, "No changes for OPC necessary.");
        } else {
            doAndReport(changeReport, "DataTag updated", () -> {
                endpoint.removeDataTag(oldSourceDataTag);
                endpoint.subscribeTag(sourceDataTag);
            });
        }
    }

    private void completeSuccessFullyWithMessage (ChangeReport changeReport, String message) {
        changeReport.appendInfo(message);
        changeReport.setState(SUCCESS);
    }

    private void doAndReport (ChangeReport changeReport, String message, Runnable runnable) {
        try {
            checkConnection();
            runnable.run();
            completeSuccessFullyWithMessage(changeReport, message);
        } catch (Exception e) {
            changeReport.appendError(e.getMessage());
            changeReport.setState(FAIL);
        }
    }

}
