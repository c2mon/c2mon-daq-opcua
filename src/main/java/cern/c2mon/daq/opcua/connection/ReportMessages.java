package cern.c2mon.daq.opcua.connection;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ReportMessages {
    TAG_ADDED("DataTag added"),
    TAG_REMOVED("DataTag removed"),
    TAG_UPDATED("DataTag updated"),
    NO_UPDATE_REQUIRED("Nothing changed, no action required"),;

    String message;
}
