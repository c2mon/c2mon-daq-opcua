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

package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.address.AddressStringParser;
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Component("controller")
@Slf4j
@RequiredArgsConstructor
public class ControllerImpl implements Controller, IDataTagChanger {

    @Getter
    private final Endpoint endpoint;

    @Autowired
    private final AliveWriter aliveWriter;

    @Override
    public synchronized void initialize(IEquipmentConfiguration config) throws ConfigurationException {
        String uaTcpType = "opc.tcp";
        EquipmentAddress address = AddressStringParser.parse(config.getAddress());
        if (!address.supportsProtocol(uaTcpType)) {
            throw new ConfigurationException(ConfigurationException.Cause.ENDPOINT_TYPES_UNKNOWN);
        }
        aliveWriter.initialize(config, address.isAliveWriterEnabled());
        endpoint.initialize(address.getServerAddressOfType(uaTcpType).getUriString());
        endpoint.connect();
        endpoint.subscribeTags(config.getSourceDataTags().values());
    }

    @Override
    public synchronized void stop () {
        endpoint.reset();
    }

    @Override
    public synchronized void refreshAllDataTags () {
        endpoint.refreshAllTags();
    }

    @Override
    public synchronized void refreshDataTag (ISourceDataTag sourceDataTag) {
        endpoint.refreshTags(Collections.singletonList(sourceDataTag));
    }

    @Override
    public String updateAliveWriter() {
        return aliveWriter.updateAndReport();
    }

    @Override
    public void subscribe (EndpointListener listener) {
        endpoint.subscribe(listener);
    }


    @Override
    public String runCommand(ISourceCommandTag tag, SourceCommandTagValue command) throws ConfigurationException {
        String result = "";
        Object arg = TypeConverter.cast(command.getValue(), command.getDataType());
        if (arg == null) {
            throw new ConfigurationException(ConfigurationException.Cause.COMMAND_VALUE_ERROR);
        }
        OPCHardwareAddress hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
        switch (hardwareAddress.getCommandType()) {
            case METHOD:
                final Object[] response = endpoint.executeMethod(tag, arg);
                log.info("Successfully executed method {} with arg {}", tag, arg);
                result = Stream.of(response).map(Object::toString).collect(Collectors.joining(", "));
                break;
            case CLASSIC:
                final int pulse = hardwareAddress.getCommandPulseLength();
                if (pulse > 0)  {
                    endpoint.executePulseCommand(tag, arg, pulse);
                } else {
                    endpoint.executeCommand(tag, arg);
                }
                break;
            default:
                throw new ConfigurationException(ConfigurationException.Cause.COMMAND_TYPE_UNKNOWN);
        }
        return result;
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
        doAndReport(changeReport, ReportMessages.TAG_REMOVED.message, () -> endpoint.removeTag(sourceDataTag));
    }

    @Override
    public void onUpdateDataTag (final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            completeSuccessFullyWithMessage(changeReport, ReportMessages.NO_UPDATE_REQUIRED.message);
        } else {
            doAndReport(changeReport, ReportMessages.TAG_UPDATED.message, () -> {
                endpoint.removeTag(oldSourceDataTag);
                endpoint.subscribeTag(sourceDataTag);
            });
        }
    }

    private void doAndReport (ChangeReport changeReport, String message, Runnable runnable) {
        try {
            reconnectIfRequired();
            runnable.run();
            completeSuccessFullyWithMessage(changeReport, message);
        } catch (Exception e) {
            changeReport.appendError(e.getMessage());
            changeReport.setState(FAIL);
        }
    }

    private void reconnectIfRequired() {
        if (!endpoint.isConnected()) {
            log.info("Connection lost. Reconnecting.");
            endpoint.reconnect();
            refreshAllDataTags();
        }
    }

    private void completeSuccessFullyWithMessage (ChangeReport changeReport, String message) {
        changeReport.appendInfo(message);
        changeReport.setState(SUCCESS);
    }
}
