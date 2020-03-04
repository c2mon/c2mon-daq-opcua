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

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;


public interface Endpoint {

    boolean isConnected ();
    void initialize () throws OPCCommunicationException;
    CompletableFuture<Void> reconnect() throws OPCCommunicationException;
    CompletableFuture<Void> reset();

    CompletableFuture<Void> subscribeTags(Collection<ISourceDataTag> dataTags) throws ConfigurationException, OPCCommunicationException;
    CompletableFuture<Void> subscribeTag(ISourceDataTag sourceDataTag) throws OPCCommunicationException;
    CompletableFuture<Void> removeDataTag(ISourceDataTag sourceDataTag) throws IllegalArgumentException;

    void refreshDataTags(Collection<ISourceDataTag> dataTags);
    void write(final OPCHardwareAddress address, final Object value);

    //for injection during testing
    void setClient(MiloClientWrapper client);

}