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

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Defines an OPC endpoint. An OPC endpoint has exactly the functionality
 * required for the TIM system. It is used to abstract from the different
 * connection types and their libraries.
 * 
 * @author Andreas Lang
 *
 */
public interface Endpoint {

    /**
     * Returns true if the endpoint is connected to a server, else returns false.
     */
    boolean isConnected();

    void initialize(EquipmentAddress address);

    /**
     * Adds a data tag to this endpoint.
     *
     * @param sourceDataTag The data tag to add.
     * @return
     */
    CompletableFuture<Void> subscribeTag(ISourceDataTag sourceDataTag);
    
    /**
     * Removes a data tag from this endpoint.
     *
     * @param sourceDataTag The data tag to remove.
     * @return
     */
    CompletableFuture<Void> removeDataTag(ISourceDataTag sourceDataTag);

    /**
     * Adds the provided data tags to the endpoint. The endpoint will send
     * updates for all added data tags.
     *
     * @param dataTags The data tags from which updates should be received.
     * @return
     */
    CompletableFuture<Void> subscribeTags(Collection<ISourceDataTag> dataTags);
    
    /**
     * Refreshes the values for the provided data tags. This will work only 
     * for already added data tags.
     * 
     * @param dataTags The data tags which should be updated.
     */
    void refreshDataTags(Collection<ISourceDataTag> dataTags);

    
    /**
     * Writes to the specified address in the OPC.
     * 
     * @param address The address to write to.
     * @param value The value to write.
     */
    void write(final OPCHardwareAddress address, final Object value);

    /**
     * Registers an endpoint listener. The endpoint will inform this listener
     * about changes.
     * 
     * @param endpointListener The listener to add.
     */
    void registerEndpointListener(EndpointListener endpointListener);

    /**
     * Unregisters an endpoint listener. The listener will no longer be informed
     * about updates.
     * 
     * @param endpointListener The listener to remove.
     */
    void unRegisterEndpointListener(EndpointListener endpointListener);
    
    /**
     * Stops everything in the endpoint and clears all configuration states.
     */
    void reset();

    EquipmentAddress getEquipmentAddress();
}
