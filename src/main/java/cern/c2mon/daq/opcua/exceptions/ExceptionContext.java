package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A complete set of reasons and situations which may trigger an OPCUAException. */
@AllArgsConstructor
public enum ExceptionContext {
    AUTH_ERROR("Could not authenticate to any server endpoint!"),
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
    URI_SYNTAX("The equipment URI has incorrect syntax"),
    HARDWARE_ADDRESS_UNKNOWN("The hardware address is not of type OPCHardwareAddress and cannot be handled. "),
    DATATAGS_EMPTY("No data tags to subscribe."),
    SECURITY("Ensure your app security settings are valid."),
    OBJINVALID("Could not resolve an object node for the command tag. Please specify the a redundant item name in the Tag's HardwareAddress to execute a method command."),
    EMPTY_SUBSCRIPTION("Cannot recreate a subscription without any monitored items."),
    SERVER_NODE("Could not get the server node."),
    NO_REDUNDANT_SERVER("No redundant server is available.");

    @Getter
    private final String message;
}
