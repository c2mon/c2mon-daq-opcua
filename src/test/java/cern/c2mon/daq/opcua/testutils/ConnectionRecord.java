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
    final boolean failoverToRedundantServer;
    final long diff;

    public static ConnectionRecord of(long reestablishedInstant, long reconnectInstant, boolean failoverToRedundantServer) {
        return new ConnectionRecord(reestablishedInstant,
                reconnectInstant,
                failoverToRedundantServer,
                (reconnectInstant >= 0 && reestablishedInstant >= 0) ? reconnectInstant - reestablishedInstant : -1L);
    }

    @Override
    public String toString() {
        return String.format("%1$14d %2$14d %3$14d %4$14s", reestablishedInstant, reconnectInstant, diff, failoverToRedundantServer);
    }
}
