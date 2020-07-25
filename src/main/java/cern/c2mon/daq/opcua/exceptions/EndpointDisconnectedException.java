package cern.c2mon.daq.opcua.exceptions;

/**
 * This exception is thrown when the session has been destroyed by one of the two parties, and further attempts are not
 * fruitful.
 */
public class EndpointDisconnectedException extends OPCUAException {

    /**
     * Creates a new EndpointDisconnectedException wrapping the throwable which caused an action to fail.
     * @param context describes the context of the action which triggered the throwable cause.
     * @param cause   the throwable causing the action to fail.
     */
    public EndpointDisconnectedException(final ExceptionContext context, final Throwable cause) {
        super(context, cause);
    }
}
