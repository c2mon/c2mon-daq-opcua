package cern.c2mon.daq.opcua.config;


import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;

import java.time.Instant;

/**
 * The server can report a server timestamp, a source timestamp, or both, where either may be null or
 * misconfigured. Possible values are:
 * SOURCE:  prefer the source  timestamp reported by the server, and fall back to the server timestamp if null.
 * SERVER:  use the server timestamp reported by the server, and fall back to the server timestamp if null.
 * CLOSEST: use the source or server timestamp which is closer to the Java time of the DAQ process.
 */
public enum TimeRecordMode {
    SOURCE, SERVER, CLOSEST;

    public Long getTime(DateTime sourceTime, DateTime serverTime) {
        if (sourceTime == null && serverTime == null) {
            return null;
        } else if (sourceTime == null || this.equals(SERVER)) {
            return serverTime.getJavaTime();
        } else if (serverTime == null || this.equals(SOURCE)) {
            return sourceTime.getJavaTime();
        } else {
            final long source = sourceTime.getJavaTime();
            final long server = serverTime.getJavaTime();
            final long reference = Instant.now().getEpochSecond();
            return (Math.abs(server - reference) > Math.abs(source - reference)) ? source : server;
        }
    }
}