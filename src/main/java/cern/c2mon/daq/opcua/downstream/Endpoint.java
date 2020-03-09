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
package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.Collection;

/**
 * Interfaces between C2MON tags and the opc endpoint.
 * Notifies C2MON of OPCUA events through the EventPublisher.
 * Passes commands to the client through the MiloClientWrapper.
 */
public interface Endpoint {

    boolean isConnected ();
    void initialize (boolean connectionLost) throws OPCCommunicationException;
    void reset();

    void subscribeTags(Collection<ISourceDataTag> dataTags) throws ConfigurationException, OPCCommunicationException;
    void subscribeTag(ISourceDataTag sourceDataTag) throws OPCCommunicationException;
    void removeDataTag(ISourceDataTag sourceDataTag) throws IllegalArgumentException;

    void refreshDataTags(Collection<ISourceDataTag> dataTags);
    void write(final OPCHardwareAddress address, final Object value);

    void recreateSubscription(UaSubscription subscription);

    //for injection during testing
    void setClient(MiloClientWrapper client);

}