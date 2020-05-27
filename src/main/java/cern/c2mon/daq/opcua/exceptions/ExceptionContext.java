package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ExceptionContext {
    CREATE_CLIENT("Could not create the OPC UA Client"),
    CONNECT("Could not connect to the OPC UA Server"),
    DISCONNECT("Could not disconnect from the OPC UA Server"),
    CREATE_SUBSCRIPTION("Could not create a subscription"),
    DELETE_SUBSCRIPTION("Could not delete the subscription"),
    DELETE_MONITORED_ITEM("Could not disconnect delete monitored items from the subscription"),
    CREATE_MONITORED_ITEM("Could not subscribe tags"),
    READ("Could not read node values"),
    WRITE("Could not write to nodes"),
    METHOD("Could not execute method"),
    METHOD_CODE("The method did not return a good status code."),
    COMMAND_CLASSIC("Could not write command value"),
    BROWSE("Browsing node failed"),
    GETOBJ("Could not get a corresponding object node"),
    AUTH_ERROR("Could not authenticate to any server endpoint!");

    @Getter
    private final String message;
}
