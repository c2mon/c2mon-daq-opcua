package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A complete set of reasons and situations which may trigger an OPCUAException. */
@AllArgsConstructor
public enum ExceptionContext {
    CONNECT("Could not connect to the OPC UA Server."),
    DISCONNECT("Could not disconnect from the OPC UA Server."),
    CREATE_SUBSCRIPTION("Could not create a subscription."),
    DELETE_SUBSCRIPTION("Could not delete the subscription."),
    DELETE_MONITORED_ITEM("Could not disconnect delete monitored items from the subscription."),
    CREATE_MONITORED_ITEM("Could not subscribe tags."),
    READ("Could not read node values."),
    WRITE("Could not write to nodes."),
    METHOD("Could not execute method."),
    METHOD_CODE("The method did not return a good status code."),
    COMMAND_CLASSIC("Could not write command value."),
    BROWSE("Browsing node failed."),
    AUTH_ERROR("Could not authenticate to any server endpoint!"),
    PKI_ERROR("Could not process the PKI base directory"),
    ADDRESS_URI("Cannot parse equipment address: Syntax of OPC URI is incorrect."),
    ADDRESS_MISSING_PROPERTIES("The address does not contain all required properties."),
    ADDRESS_INVALID_PROPERTIES("The address does contains invalid properties."),
    ENDPOINT_TYPES_UNKNOWN("No supported protocol found."),
    HARDWARE_ADDRESS_UNKNOWN("The hardware address is not of type OPCHardwareAddress and cannot be handled. "),
    MISSING_URI("The equipment address does not have an URI."),
    DATATAGS_EMPTY("No data tags to subscribe."),
    COMMAND_TYPE_UNKNOWN("The provided command type is unknown."),
    COMMAND_VALUE_ERROR("Provided command value could not be processed. Check data type and value."),
    SECURITY("Ensure your app security settings are valid."),
    OBJINVALID("Could not resolve an object node for the command tag. Please specify the a redundant item name in the Tag's HardwareAddress to execute a method command."),
    EMPTY_SUBSCRIPTION("Cannot recreate a subscription without any monitored items."),
    SERVER_NODE("Could not get the server node.");

    @Getter
    private final String message;
}
