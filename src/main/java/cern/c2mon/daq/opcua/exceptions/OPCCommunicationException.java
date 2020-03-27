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
package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;

/**
 * Exception while communicating with the OPC server. This exception can be due
 * to a temporary reason (network down). It might work to retry after this
 * exception.
 * 
 * @author Andreas Lang
 *
 */
public class OPCCommunicationException extends RuntimeException {

    @AllArgsConstructor
    public enum Cause {
        CREATE_CLIENT("Could not create the OPC UA Client"),
        CONNECT("Could not connect to the OPC UA Server"),
        DISCONNECT("Could not disconnect from the OPC UA Server"),
        CREATE_SUBSCRIPTION("Could not create a subscription"),
        DELETE_SUBSCRIPTION("Could not delete the subscription"),
        DELETE_MONITORED_ITEM("Could not disconnect delete monitored items from the subscription"),
        SUBSCRIBE_TAGS("Could not subscribe tags"),
        READ("Could not read node values"),
        WRITE("Could not write to nodes"),
        BROWSE("Browsing node failed"),
        ENDPOINTS("The server does not offer any endpoints matching the configuration");

        public final String message;

    }

    public OPCCommunicationException(final Cause type, final Exception e) {
        super(type.message, e);
    }

    public OPCCommunicationException(final Cause type) {
        super(type.message);
    }
}
