/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A complete set of reasons and situations which may trigger an OPCUAException. */
@AllArgsConstructor
public enum ExceptionContext {
    AUTH_ERROR("Could not authenticate to any server endpoint!"),
    BAD_ALIVE_TAG_INTERVAL("Cannot start the Alive Writer with an interval of 0."),
    BAD_ALIVE_TAG("The AliveTag is not defined in the SourceDataTags, cannot start the Alive Writer."),
    BROWSE("Browsing node failed."),
    COMMAND_TYPE_UNKNOWN("The provided command type is unknown."),
    COMMAND_VALUE_ERROR("Provided command value could not be processed. Check data type and value."),
    CONNECT("Could not connect to the OPC UA Server."),
    CREATE_MONITORED_ITEM("Could not subscribe tags."),
    CREATE_SUBSCRIPTION("Could not create a subscription."),
    DISCONNECT("Could not disconnect from the OPC UA Server."),
    DELETE_SUBSCRIPTION("Could not delete the subscription."),
    DELETE_MONITORED_ITEM("Could not disconnect delete monitored items from the subscription."),
    READ("Could not read node values."),
    WRITE("Could not write to nodes."),
    METHOD("Could not execute method."),
    METHOD_CODE("The method did not return a good status code."),
    COMMAND_CLASSIC("Could not write command value."),
    PKI_ERROR("Could not process the PKI base directory"),
    URI_MISSING("The equipment address must specify at least one URI."),
    URI_NOT_FOUND("The URI of the returned EndpointDescriptions could not be found when modified as configured. Are the settings correct for the network?"),
    URI_SYNTAX("The equipment URI has incorrect syntax"),
    HARDWARE_ADDRESS_TYPE("The hardware address is not of type OPCHardwareAddress and cannot be handled. "),
    DATATAGS_EMPTY("No data tags to subscribe."),
    SECURITY("Ensure your app security settings are valid."),
    OBJ_INVALID("Could not resolve an object node for the command tag. Please specify the a redundant item name in the Tag's HardwareAddress to execute a method command."),
    EMPTY_SUBSCRIPTION("Cannot recreate a subscription without any monitored items."),
    SERVER_NODE("Could not get the server node."),
    NO_REDUNDANT_SERVER("No redundant server is available.");
    @Getter
    private final String message;
}
