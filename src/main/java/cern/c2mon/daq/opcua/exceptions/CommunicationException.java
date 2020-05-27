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

    public static void rethrow(ExceptionContext context, Exception e) throws CommunicationException, ConfigurationException {
        if (e instanceof InterruptedException || e instanceof ExecutionException) {
            handleThrowable(context, e, e.getCause());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void handleThrowable(ExceptionContext context, Exception e, Throwable cause) throws CommunicationException, ConfigurationException {
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            StatusCodes.lookup(code).ifPresentOrElse(
                    s -> log.error("Failure description: {}", Arrays.toString(s)),
                    () -> log.error("Reasons unknown."));
            if (RECONNECT.contains(code)) {
                throw new ConfigurationException(context, e);
            }
        }
        throw new CommunicationException(context, e);
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

