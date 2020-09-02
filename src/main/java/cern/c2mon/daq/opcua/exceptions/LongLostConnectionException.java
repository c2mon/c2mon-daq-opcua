package cern.c2mon.daq.opcua.exceptions;

/**
 * This exception is thrown when the client has been disconnected for so long that any retries of an action on the
 * server are unlikely to succeed.
 */
public class LongLostConnectionException extends OPCUAException {

    /**
     * Creates a new LongLostConnectionException wrapping the throwable which caused an action to fail.
     * @param context describes the context of the action which triggered the throwable cause.
     * @param cause   the throwable causing the action to fail.
     */
    public LongLostConnectionException(final ExceptionContext context, final Throwable cause) {
        super(context, cause);
    }

}
