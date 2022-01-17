/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
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
 * An exception which is unsolvable without a configuration change.
 */
public class ConfigurationException extends OPCUAException {

    /**
     * Creates a new ConfigurationException.
     * @param context describes the misconfiguration what triggered the exception.
     */
    public ConfigurationException (final ExceptionContext context) {
        super(context);
    }

    /**
     * Creates a new ConfigurationException wrapping the throwable which caused an action to fail.
     * @param context describes the context of the action which triggered the throwable cause.
     * @param cause   the throwable causing the action to fail.
     */
    public ConfigurationException (ExceptionContext context, final Throwable cause) {
        super(context, cause);
    }
    /**
     * Called Creates a new LongLostConnectionException wrapping the throwable which caused an action to fail.
     * @param context describes the context of the action which triggered the throwable cause.
     * @param details additional information on the cause of the error.
     */
    public ConfigurationException (ExceptionContext context, final String details) {
        super(context.getMessage() + details);
    }
}
