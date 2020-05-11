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

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * Exception while communicating with the OPC server. This exception can be due
 * to a temporary reason (network down). It might work to retry after this
 * exception.
 * 
 * @author Andreas Lang
 *
 */
@Slf4j
public class CommunicationException extends OPCUAException {

    /**
     * Status Codes representing states which impact further interaction with the server
     */
    private static final ImmutableSet<Long> TERMINAL = ImmutableSet.<Long>builder().add(Bad_InternalError, Bad_OutOfMemory, Bad_Shutdown, Bad_ServerHalted, Bad_NonceInvalid, Bad_TooManySessions, Bad_TcpInternalError, Bad_DeviceFailure, Bad_SensorFailure, Bad_OutOfService).build();

    /**
     * Status Codes representing states that could be solved by disconnecting and reconnecting
     */
    private static final ImmutableSet<Long> RECONNECT = ImmutableSet.<Long>builder().add(Bad_ServerNotConnected, Bad_SessionIdInvalid, Bad_SessionClosed, Bad_SessionNotActivated, Bad_NoCommunication, Bad_NotConnected, Bad_Disconnect).build();

    /**
     * Status Codes representing states where retrying an operation again may work
     */
    private static final ImmutableSet<Long> RETRY_LATER = ImmutableSet.<Long>builder().add(Bad_ResourceUnavailable, Uncertain_NoCommunicationLastUsableValue, Uncertain_SensorNotAccurate, Bad_TooManyPublishRequests).build();

    /**
     * Status Codes returned by the server hinting at misconfigurations.
     */
    private static final ImmutableSet<Long> CONFIG = ImmutableSet.<Long>builder().add(Bad_NodeIdUnknown, Bad_ServerUriInvalid, Bad_FilterNotAllowed, Bad_ServerNameMissing, Bad_DiscoveryUrlMissing, Bad_DeadbandFilterInvalid, Bad_ConfigurationError, Bad_TcpEndpointUrlInvalid, Bad_MethodInvalid, Bad_ArgumentsMissing, Bad_WriteNotSupported, Bad_HistoryOperationUnsupported, Bad_HistoryOperationInvalid, Bad_NoDeleteRights, Bad_TargetNodeIdInvalid, Bad_SourceNodeIdInvalid, Bad_NodeIdRejected, Bad_FilterOperandInvalid).build();


    /**
     * Status Codes returned by the server hinting at misconfigurations.
     */
    private static final ImmutableSet<Long> SECURITY_CONFIG = ImmutableSet.<Long>builder().add(Bad_UserSignatureInvalid, Bad_UserAccessDenied, Bad_CertificateHostNameInvalid, Bad_ApplicationSignatureInvalid, Bad_CertificateIssuerUseNotAllowed, Bad_CertificateIssuerTimeInvalid, Bad_CertificateIssuerRevoked).build();


    public static void rethrow(ExceptionContext context, Exception e) throws CommunicationException {
        if (e instanceof InterruptedException || e instanceof ExecutionException) {
            handleThrowable(context, e, e.getCause());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void handleThrowable(ExceptionContext context, Exception e, Throwable cause) throws CommunicationException {
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            StatusCodes.lookup(code).ifPresentOrElse(
                    s -> log.error("Failure description: {}", Arrays.toString(s)),
                    () -> log.error("Reasons unknown."));
        }
        throw new CommunicationException(context, e);
    }

    public static boolean reconnectRequired(CommunicationException e) {
        final var cause = e.getCause();
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            return RECONNECT.contains(code);
        }
        return false;
    }

    public static boolean reconfigurationRequired(CommunicationException e) {
        final var cause = e.getCause();
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            return CONFIG.contains(code);
        }
        return false;
    }

    public static boolean isTerminal(CommunicationException e) {
        final var cause = e.getCause();
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            return TERMINAL.contains(code);
        }
        return false;
    }

    /**
     * Wrap a {@link Throwable} as an OPCCommunicationException
     * @param ctx The context the exception occurred in
     * @param e the throwable to wrap as an OPCCommunicationException
     */
    public CommunicationException(final ExceptionContext ctx, final Throwable e) {
        super(ctx.getMessage(), e);
    }

    /**
     * Create an new {@link CommunicationException}
     * @param ctx The context the exception occurred in
     */
    public CommunicationException(final ExceptionContext ctx) {
        super(ctx.getMessage());
    }
}

