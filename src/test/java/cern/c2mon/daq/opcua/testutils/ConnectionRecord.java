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
