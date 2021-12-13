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
package cern.c2mon.daq.opcua.testutils;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class ConnectionRecord {
    long reestablishedInstant;
    long reconnectInstant;
    long diff = 0;

    public static ConnectionRecord of(long reestablishedInstant, long reconnectInstant) {
        return new ConnectionRecord(reestablishedInstant,
                reconnectInstant,
                (reconnectInstant > 0 && reestablishedInstant > 0) ? reconnectInstant - reestablishedInstant : -1L);
    }

    public void setReconnectInstant(long reconnectInstant) {
        this.reconnectInstant = reconnectInstant;
        this.diff = reestablishedInstant > 0 ? reconnectInstant - reestablishedInstant : this.diff;
    }

    @Override
    public String toString() {
        return String.format("%1$14d %2$14d %3$14d", reestablishedInstant, reconnectInstant, diff);
    }
}
