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

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Slf4j
@RequiredArgsConstructor
public class ControllerImpl implements Controller, IDataTagChanger {

    @Getter
    private final Endpoint endpoint;
    @Setter
    private IEquipmentConfiguration config;

    public void initialize (IEquipmentConfiguration config) throws ConfigurationException {
        synchronized (this) {
            this.config = config;
        }
        initialize(false);
    }
    public void initialize () throws ConfigurationException {
        initialize(false);
    }

    private void initialize (boolean connectionLost) throws ConfigurationException {
        endpoint.initialize(connectionLost);
        synchronized (this) {
            endpoint.subscribeTags(config.getSourceDataTags().values());
        }
    }

    public synchronized void stop () {
        endpoint.reset();
    }

    public void checkConnection () throws ConfigurationException {
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
    public void subscribe (EndpointListener listener) {
        endpoint.subscribe(listener);
    }

    @Override
    public void onAddDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        doAndReport(changeReport, ReportMessages.TAG_ADDED.message, () -> {
            endpoint.subscribeTag(sourceDataTag);
            refreshDataTag(sourceDataTag);
        });
    }

    @Override
    public void onRemoveDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        doAndReport(changeReport, ReportMessages.TAG_REMOVED.message, () -> endpoint.removeDataTag(sourceDataTag));
    }

    @Override
    public void onUpdateDataTag (final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            completeSuccessFullyWithMessage(changeReport, ReportMessages.NO_UPDATE_REQUIRED.message);
        } else {
            doAndReport(changeReport, ReportMessages.TAG_UPDATED.message, () -> {
                endpoint.removeDataTag(oldSourceDataTag);
                endpoint.subscribeTag(sourceDataTag);
            });
        }
    }

    @Override
    public void runCommand(ISourceCommandTag tag, SourceCommandTagValue value) throws ConfigurationException {
        endpoint.executeCommand(tag, value);

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

    private void completeSuccessFullyWithMessage (ChangeReport changeReport, String message) {
        changeReport.appendInfo(message);
        changeReport.setState(SUCCESS);
    }
}
