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
package cern.c2mon.daq.opcua.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gathers network metrics through Oshi. Specified Class from @{link https://github.com/jrask/micrometer-oshi-binder}
 * with some changes to go with Micrometer 1.1.0. Open issue for Micrometer integration: @{link
 * https://github.com/micrometer-metrics/micrometer/issues/1274}
 */
@RequiredArgsConstructor
public class NetworkMetrics implements MeterBinder {

    private static final int MAX_REFRESH_TIME = 5_000;

    @AllArgsConstructor
    enum NetworkMetric {
        BYTES_RECEIVED("system.network.bytes.received"),
        BYTES_SENT("system.network.bytes.sent"),
        PACKETS_RECEIVED("system.network.packets.received"),
        PACKETS_SENT("system.network.packets.sent");
        String name;
    }

    private final CachedNetworkStats networkStats = new CachedNetworkStats(new SystemInfo().getHardware().getNetworkIFs());
    private final Iterable<Tag> tags;

    /**
     * Binds relevant network tags to the Micrometer meterRegisty
     * @param meterRegistry the meterRegistry to bind to
     */
    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        for (NetworkMetric networkMetric : NetworkMetric.values()) {
            FunctionCounter.builder(networkMetric.name, networkMetric, this::getNetworkMetricAsLong)
                    .tags(tags)
                    .register(meterRegistry);
        }
    }

    private long getNetworkMetricAsLong(cern.c2mon.daq.opcua.metrics.NetworkMetrics.NetworkMetric type) {
        networkStats.refresh();

        switch (type) {
            case BYTES_SENT:
                return networkStats.bytesSent.get();
            case PACKETS_SENT:
                return networkStats.packetsSent.get();
            case BYTES_RECEIVED:
                return networkStats.bytesReceived.get();
            case PACKETS_RECEIVED:
                return networkStats.packetsReceived.get();
            default:
                throw new IllegalStateException("Unknown NetworkMetrics: " + type.name());
        }
    }

    @RequiredArgsConstructor
    private static class CachedNetworkStats {

        private final Lock lock = new ReentrantLock();
        private final List<NetworkIF> networks;
        private long lastRefresh;

        private AtomicLong bytesReceived = new AtomicLong();
        private AtomicLong bytesSent = new AtomicLong();
        private AtomicLong packetsReceived = new AtomicLong();
        private AtomicLong packetsSent = new AtomicLong();


        private void refresh() {
            if (lock.tryLock()) {
                try {
                    doRefresh();
                } finally {
                    lock.unlock();
                }
            }
        }

        private void doRefresh() {

            if (System.currentTimeMillis() - lastRefresh < MAX_REFRESH_TIME) {
                return;
            }

            long currentBytesReceived = 0;
            long currentBytesSent = 0;
            long currentPacketsReceived = 0;
            long currentPacketsSent = 0;

            for (NetworkIF nif : networks) {
                nif.updateAttributes();
                currentBytesReceived += nif.getBytesRecv();
                currentBytesSent += nif.getBytesSent();
                currentPacketsReceived += nif.getPacketsRecv();
                currentPacketsSent += nif.getPacketsSent();
            }

            this.bytesReceived.set(currentBytesReceived);
            this.bytesSent.set(currentBytesSent);
            this.packetsReceived.set(currentPacketsReceived);
            this.packetsSent.set(currentPacketsSent);
            this.lastRefresh = System.currentTimeMillis();
        }
    }
}
