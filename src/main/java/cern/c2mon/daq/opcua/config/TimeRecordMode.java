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
package cern.c2mon.daq.opcua.config;


/**
 * The server can report a server timestamp, a source timestamp, or both, where either may be null or
 * badly configured. Some SDKs report unset values as Unix 0, which will be interpreted as 1970. Possible values are:
 * SOURCE:  prefer the source  timestamp reported by the server, and fall back to the server timestamp if null.
 * SERVER:  use the server timestamp reported by the server, and fall back to the source timestamp if null.
 * CLOSEST: use the source or server timestamp which is closer to the Java time of the DAQ process.
 */
public enum TimeRecordMode {
    SOURCE, SERVER, CLOSEST;

    /**
     * Get the time to use for a DataValue update for the source server time values returned by the OPC UA values.
     * @param source the nullable source time value
     * @param server the nullable server time value
     * @return the appropriate time as configured.
     */
    public Long getTime(Long source, Long server) {
        if (source == null && server == null) {
            return null;
        } else if (source == null || (this.equals(SERVER) && server != null)) {
            return server;
        } else if (server == null || this.equals(SOURCE)) {
            return source;
        } else {
            final long reference = System.currentTimeMillis();
            return (Math.abs(server - reference) > Math.abs(source - reference)) ? source : server;
        }
    }
}
