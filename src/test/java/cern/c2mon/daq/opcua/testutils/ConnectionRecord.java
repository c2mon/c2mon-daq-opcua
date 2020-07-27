package cern.c2mon.daq.opcua.testutils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ConnectionRecord {
    final long reestablishedInstant;
    final long reconnectInstant;
    final boolean explicitFailover;
    final boolean failoverToRedundantServer;
    final long diff;

    public static ConnectionRecord of(long reestablishedInstant, long reconnectInstant, boolean failoverToRedundantServer, boolean explicitFailover) {
        return new ConnectionRecord(reestablishedInstant,
                reconnectInstant,
                explicitFailover,
                failoverToRedundantServer,
                (reconnectInstant >= 0 && reestablishedInstant >= 0) ? reconnectInstant - reestablishedInstant : -1L);
    }

    @Override
    public String toString() {
        return String.format("%1$14s", reestablishedInstant) + String.format(" %1$14s", reconnectInstant) + String.format(" %1$4s", diff) + " " + String.format(" %1$14s", explicitFailover) + " " + String.format(" %1$14s", failoverToRedundantServer);
    }
}
