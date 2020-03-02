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
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.EndpointTypesUnknownException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Slf4j
public class EndpointController implements IDataTagChanger {
    public static final String UA_TCP_TYPE = "opc.tcp";

    private List<EquipmentAddress> opcAddresses;
    private Endpoint endpoint;
    private IEquipmentMessageSender sender;
    private IEquipmentConfiguration configuration;

    public EndpointController (final Endpoint endpoint,
                               final List<EquipmentAddress> opcAddresses,
                               final IEquipmentMessageSender sender,
                               final IEquipmentConfiguration configuration) {
        this.endpoint = endpoint;
        this.opcAddresses = opcAddresses;
        this.sender = sender;
        this.configuration = configuration;

    }

    public void initialize () throws EndpointTypesUnknownException {
        try {
            startEndpoint().get();
        } catch (OPCCommunicationException|InterruptedException|ExecutionException e) {
            checkConnection();
        }
    }

    private CompletableFuture<Void> startEndpoint() throws OPCCommunicationException {
        return endpoint.reset()
                .thenRun(() -> endpoint.initialize(getEquipmentAddress()))
                .whenComplete((aVoid, e) -> {
                    if (e != null) {
                        sender.confirmEquipmentStateIncorrect(e.getMessage());
                    } else {
                        endpoint.subscribeTags(configuration.getSourceDataTags().values());
                        sender.confirmEquipmentStateOK("Connected to " + endpoint.getEquipmentAddress());
                    }
                });
    }

    public void registerEndpointListener (EndpointListener listener) {
        this.endpoint.registerEndpointListener(listener);
    }

    public synchronized void stop () {
        endpoint.reset();
    }

    public void checkConnection () {
        if (!endpoint.isConnected()) {
            endpoint.reset()
                    .thenRun(this::startEndpoint)
                    .exceptionally(e -> {throw new OPCCommunicationException("Cannot establish connection", e);});
            // TODO refresh?
        }
    }

    public synchronized void refreshAllDataTags () {
        endpoint.refreshDataTags(configuration.getSourceDataTags().values());
    }

    public synchronized void refreshDataTag (ISourceDataTag sourceDataTag) {
        endpoint.refreshDataTags(Collections.singletonList(sourceDataTag));
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

    private EquipmentAddress getEquipmentAddress () {
        Optional<EquipmentAddress> matchingAddress = this.opcAddresses.stream().filter(o -> o.getProtocol().equals(UA_TCP_TYPE)).findFirst();
        if (!matchingAddress.isPresent()) {
            throw new EndpointTypesUnknownException("No supported protocol found. createEndpoint - Endpoint creation for '" + this.opcAddresses + "' failed. Stop Startup.");
        }
        return matchingAddress.get();
    }
}
