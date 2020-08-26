package cern.c2mon.daq.opcua.exceptions;

import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OPCUAExceptionTest {

    static ExceptionContext c = ExceptionContext.AUTH_ERROR;


    @Test
    public void uaExceptionOfTypeConfigShouldThrowConfigurationException() {
        Throwable e = new UaException(StatusCodes.Bad_NodeIdUnknown);
        assertTrue(OPCUAException.of(c, e, false) instanceof ConfigurationException);
    }

    @Test
    public void unknownHostShouldThrowConfigurationException() {
        Throwable e = new UnknownHostException();
        assertTrue(OPCUAException.of(c, e, false) instanceof ConfigurationException);
    }

    @Test
    public void endpointStoppedShouldThrowEndpointDisconnectedException() {
        Throwable e = new UaException(StatusCodes.Bad_SecureChannelIdInvalid);
        assertTrue(OPCUAException.of(c, e, false) instanceof EndpointDisconnectedException);
    }

    @Test
    public void endpointStoppedShouldThrowEndpointDisconnectedExceptionEvenIfLongLost() {
        Throwable e = new UaException(StatusCodes.Bad_SecureChannelIdInvalid);
        assertTrue(OPCUAException.of(c, e, true) instanceof EndpointDisconnectedException);
    }

    @Test
    public void alreadyAttemptingResubscriptionTooLongThrowsLongLostConnectionException() {
        Throwable e = new IOException();
        assertTrue(OPCUAException.of(c, e, true) instanceof LongLostConnectionException);
    }

    @Test
    public void anyOtherExceptionShouldThrowCommunicationException() {
        Throwable e = new IOException();
        assertTrue(OPCUAException.of(c, e, false) instanceof CommunicationException);
    }

    @Test
    public void securityIssueReturnsTrue() {
        UaException e = new UaException(StatusCodes.Bad_CertificateHostNameInvalid);
        assertTrue(OPCUAException.isSecurityIssue(e));
    }

    @Test
    public void nonSecurityIssueReturnsFalse() {
        UaException e = new UaException(StatusCodes.Bad_AggregateInvalidInputs);
        assertFalse(OPCUAException.isSecurityIssue(e));
    }
}
