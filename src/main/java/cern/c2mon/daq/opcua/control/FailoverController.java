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
package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;

/**
 * Implementing classes are {@link ConcreteController}s that are connected to one or more servers in a non-transparent redundant
 * server set. They support the failover between servers in case of error.
 */
public interface FailoverController {

    /**
     * Initiates a server failover. This method is periodically  when the client loses connectivity with the active
     * Server until connection can be reestablished.
     * @throws OPCUAException if connecting to a server of the redundant server set failed.
     */
    void switchServers() throws OPCUAException;
}
