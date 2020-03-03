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
import cern.c2mon.daq.opcua.exceptions.EndpointTypesUnknownException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Slf4j
public class EndpointControllerImpl implements EndpointController, IDataTagChanger {

    private Endpoint endpoint;
    private IEquipmentConfiguration config;
    private EventPublisher publisher;

    public EndpointControllerImpl (final Endpoint endpoint,
                                   final IEquipmentConfiguration config,
                                   final EventPublisher publisher) {
        this.endpoint = endpoint;
        this.config = config;
        this.publisher = publisher;
    }

    public void initialize () throws EndpointTypesUnknownException, OPCCommunicationException {
        endpoint.reset();
        endpoint.initialize();
        endpoint.subscribeTags(config.getSourceDataTags().values());
    }

    @Override
    public void subscribe (EndpointListener listener) {
        publisher.subscribe(listener);
    }

    public synchronized void stop () {
        endpoint.reset();
    }

    public void checkConnection () throws OPCCommunicationException {
        if (!endpoint.isConnected()) {
            endpoint.reset()
                    .thenRun(this::initialize)
                    .exceptionally(e -> {throw new OPCCommunicationException("Cannot establish connection", e);});
            // TODO refresh?
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
        tryAndReport(changeReport, "DataTag added", () -> {
            endpoint.subscribeTag(sourceDataTag);
            refreshDataTag(sourceDataTag);
        });
    }

    @Override
    public void onRemoveDataTag (final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        tryAndReport(changeReport, "DataTag removed", () -> endpoint.removeDataTag(sourceDataTag));
    }

    @Override
    public void onUpdateDataTag (final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            completeSuccessFullyWithMessage(changeReport, "No changes for OPC necessary.");
        } else {
            tryAndReport(changeReport, "DataTag updated", () -> {
                endpoint.removeDataTag(oldSourceDataTag);
                endpoint.subscribeTag(sourceDataTag);
            });
        }
    }

    private void completeSuccessFullyWithMessage (ChangeReport changeReport, String message) {
        changeReport.appendInfo(message);
        changeReport.setState(SUCCESS);
    }

    private void tryAndReport (ChangeReport changeReport, String message, Runnable runnable) {
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
