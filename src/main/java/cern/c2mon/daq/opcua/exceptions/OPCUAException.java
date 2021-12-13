/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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

import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import com.google.common.collect.ImmutableSet;
import org.eclipse.milo.opcua.stack.core.UaException;

import java.net.UnknownHostException;
import java.util.Collection;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * An abstract EqIOException occurring in the context of communication with an OPC UA server.
 */
public abstract class OPCUAException extends EqIOException {

    /**
     * Status Codes returned by the server hinting at misconfigurations.
     */
    private static final Collection<Long> CONFIG = ImmutableSet.<Long>builder().add(
            Bad_NodeIdUnknown,
            Bad_ServerUriInvalid,
            Bad_FilterNotAllowed,
            Bad_ServerNameMissing,
            Bad_DiscoveryUrlMissing,
            Bad_DeadbandFilterInvalid,
            Bad_ConfigurationError,
            Bad_TcpEndpointUrlInvalid,
            Bad_MethodInvalid,
            Bad_ArgumentsMissing,
            Bad_WriteNotSupported,
            Bad_HistoryOperationUnsupported,
            Bad_HistoryOperationInvalid,
            Bad_NoDeleteRights,
            Bad_TargetNodeIdInvalid,
            Bad_SourceNodeIdInvalid,
            Bad_NodeIdRejected,
            Bad_FilterOperandInvalid,
            Bad_ServiceUnsupported).build();

    /**
     * Status Codes returned by the server hinting at misconfigurations of security settings.
     */
    private static final Collection<Long> SECURITY_CONFIG = ImmutableSet.<Long>builder().add(
            Bad_UserSignatureInvalid,
            Bad_UserAccessDenied,
            Bad_CertificateHostNameInvalid,
            Bad_ApplicationSignatureInvalid,
            Bad_CertificateIssuerUseNotAllowed,
            Bad_CertificateIssuerTimeInvalid,
            Bad_CertificateIssuerRevoked).build();


    private static final Collection<Long> ENDPOINT_DISCONNECTED = ImmutableSet.<Long>builder().add(
            Bad_SessionClosed,
            Bad_SecureChannelClosed).build();

    protected OPCUAException (final ExceptionContext context) {
        super(context.getMessage());
    }

    protected OPCUAException (final ExceptionContext context, final Throwable e) {
        super(context.getMessage(), e);
    }

    protected OPCUAException (final String cause) {
        super(cause);
    }

    /**
     * A static factory method called when the execution of an action on the server failed. The throwable causing this
     * failure is wrapped in a subclass of OPCUAException best describing the context of the exception, and the
     * prospects of retrying the action.
     * @param context              The context that the  throwable e occurred in, used to form an informative error
     *                             message.
     * @param e                    The throwable which caused the action on the server to fail originally.
     * @param disconnectionTooLong A boolean indicating whether the connection between client and server has been
     *                             severed for a significant amount of time.
     * @return A concrete OPCUAException of type {@link ConfigurationException}, if the original error hints at an issue
     * with the equipment configuration, of type {@link EndpointDisconnectedException} if the endpoint has been
     * connected by the DAQ. If the connection has been severed for longer than all retries are expected to take, a
     * {@link LongLostConnectionException} is returned, otherwise a {@link CommunicationException}.
     */
    public static OPCUAException of (ExceptionContext context, Throwable e, boolean disconnectionTooLong) {
        if (isConfigIssue(e)) {
            return new ConfigurationException(context, e);
        } else if (isEndpointDisconnectedIssue(e)) {
            return new EndpointDisconnectedException(context, e);
        } else if (disconnectionTooLong) {
            return new LongLostConnectionException(context, e.getCause());
        }
        return new CommunicationException(context, e);
    }

    /**
     * Checks whether the UaException e was throws due to a general security issue.
     * @param e the UaException to classify.
     * @return true is the exception e is due to a security general issue.
     */
    public static boolean isSecurityIssue (UaException e) {
        return SECURITY_CONFIG.contains(e.getStatusCode().getValue());
    }

    private static boolean isConfigIssue (Throwable e) {
        return e instanceof UnknownHostException || e instanceof UaException && CONFIG.contains(((UaException) e).getStatusCode().getValue());
    }

    private static boolean isEndpointDisconnectedIssue (Throwable e) {
        return e instanceof UaException && ENDPOINT_DISCONNECTED.contains(((UaException) e).getStatusCode().getValue());
    }

}
