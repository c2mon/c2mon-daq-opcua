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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static cern.c2mon.daq.opcua.config.TimeRecordMode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TimeRecordModeTest {
    static Long sourceTime = System.currentTimeMillis();
    static Long serverTime = System.currentTimeMillis()- TimeUnit.DAYS.toMillis(2);

    @Test
    public void sourceShouldReturnSourceIfBothAreSet() {
        assertEquals(sourceTime, SOURCE.getTime(sourceTime, serverTime));
    }

    @Test
    public void serverShouldReturnSourceIfNotSet() {
        assertEquals(sourceTime, SERVER.getTime(sourceTime, null));
    }

    @Test
    public void serverShouldReturnServerIfBothAreSet() {
        assertEquals(serverTime, SERVER.getTime(sourceTime, serverTime));
    }

    @Test
    public void sourceShouldReturnServerIfNotSet() {
        assertEquals(serverTime, SOURCE.getTime(null, serverTime));
    }

    @Test
    public void returnNullIfBothAreNull() {
        assertNull(SOURCE.getTime(null, null));
        assertNull(SERVER.getTime(null, null));
        assertNull(CLOSEST.getTime(null, null));
    }

    @Test
    public void closestShouldReturnSourceIfCloser() {
        assertEquals(sourceTime, CLOSEST.getTime(sourceTime, serverTime));
    }

    @Test
    public void closestShouldReturnServerIfCloser() {
        final long expected = System.currentTimeMillis();
        assertEquals(expected, CLOSEST.getTime(sourceTime, expected));
    }

}
