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

import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.daq.opcua.connection.ClientWrapper;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.Collection;

/**
 * Interfaces between C2MON tags and the opc endpoint.
 * Notifies C2MON of OPCUA events through the EventPublisher.
 * Passes commands to the client through the MiloClientWrapper.
 */
public interface Endpoint {

    boolean isConnected ();
    void initialize(String uri);
    void connect(boolean connectionLost);
    void reset();
    void subscribe (EndpointListener listener);

    void subscribeTags(Collection<ISourceDataTag> dataTags) throws ConfigurationException;
    void subscribeTag(ISourceDataTag sourceDataTag);
    void removeDataTag(ISourceDataTag sourceDataTag);

    void refreshDataTags(Collection<ISourceDataTag> dataTags);
    void writeAlive(final OPCHardwareAddress address, final Object value);

    void executeCommand(ISourceCommandTag tag, SourceCommandTagValue value) throws ConfigurationException;

    void recreateSubscription(UaSubscription subscription);

    //for injection during testing
    void setWrapper(ClientWrapper wrapper);
    EventPublisher getPublisher();
    TagSubscriptionMapper getMapper();
}