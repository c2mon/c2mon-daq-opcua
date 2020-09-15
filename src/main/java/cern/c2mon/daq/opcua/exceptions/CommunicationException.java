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

/**
 * This exception is thrown when communicating with the OPC UA server fails for reasons that are not mapped to
 * misconfiguration. This exception can be due to a temporary reason (network down). It may be fruitful to retry an
 * action after this exception.
 */
public class CommunicationException extends OPCUAException {

    /**
     * Creates a new CommunicationException wrapping the throwable which caused an action to fail.
     * @param context The context the exception occurred in.
     * @param cause   the throwable to wrap as an OPCCommunicationException.
     */
    public CommunicationException(final ExceptionContext context, final Throwable cause) {
        super(context, cause);
    }

    /**
     * Create an new CommunicationException.
     * @param context gives more details about cause and context of the exception.
     */
    public CommunicationException(final ExceptionContext context) {
        super(context);
    }
}
