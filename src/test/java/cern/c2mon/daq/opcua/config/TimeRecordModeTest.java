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
