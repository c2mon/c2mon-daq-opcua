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