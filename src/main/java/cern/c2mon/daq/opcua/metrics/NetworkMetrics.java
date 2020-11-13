/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
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
import lombok.RequiredArgsConstructor;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gathers network metrics through Oshi.
 * Specified Class from @{link https://github.com/jrask/micrometer-oshi-binder} with some changes to go with Micrometer
 * 1.1.0. Open issue for Micrometer integration: @{link https://github.com/micrometer-metrics/micrometer/issues/1274}
 */
@RequiredArgsConstructor
public class NetworkMetrics implements MeterBinder {

    private static final int MAX_REFRESH_TIME = 5_000;

    enum NetworkMetric {
        BYTES_RECEIVED, BYTES_SENT, PACKETS_RECEIVED, PACKETS_SENT
    }

    private final CachedNetworkStats networkStats = new CachedNetworkStats(new SystemInfo().getHardware().getNetworkIFs());
    private final Iterable<Tag> tags;

    /**
     * Binds relevant network tags to the Micrometer meterRegisty
     * @param meterRegistry the meterRegistry to bind to
     */
    @Override
    public void bindTo(MeterRegistry meterRegistry) {

        FunctionCounter.builder("system.network.bytes.received",
                NetworkMetric.BYTES_RECEIVED, this::getNetworkMetricAsLong)
                .tags(tags)
                .register(meterRegistry);

        FunctionCounter.builder("system.network.bytes.sent",
                NetworkMetric.BYTES_SENT, this::getNetworkMetricAsLong)
                .tags(tags)
                .register(meterRegistry);

        FunctionCounter.builder("system.network.packets.received",
                NetworkMetric.PACKETS_RECEIVED, this::getNetworkMetricAsLong)
                .tags(tags)
                .register(meterRegistry);

        FunctionCounter.builder("system.network.packets.sent",
                NetworkMetric.PACKETS_SENT, this::getNetworkMetricAsLong)
                .tags(tags)
                .register(meterRegistry);
    }

    private long getNetworkMetricAsLong(cern.c2mon.daq.opcua.metrics.NetworkMetrics.NetworkMetric type) {
        networkStats.refresh();

        switch (type) {
            case BYTES_SENT:
                return networkStats.bytesSent;
            case PACKETS_SENT:
                return networkStats.packetsSent;
            case BYTES_RECEIVED:
                return networkStats.bytesReceived;
            case PACKETS_RECEIVED:
                return networkStats.packetsReceived;
            default:
                throw new IllegalStateException("Unknown NetworkMetrics: " + type.name());
        }
    }

    private static class CachedNetworkStats {

        private final Lock lock = new ReentrantLock();

        private final List<NetworkIF> networks;
        private long lastRefresh = 0;

        public volatile long bytesReceived;
        public volatile long bytesSent;
        public volatile long packetsReceived;
        public volatile long packetsSent;

        public CachedNetworkStats(List<NetworkIF> networkIFs) {
            this.networks = networkIFs;
        }

        public void refresh() {
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

            long bytesReceived = 0;
            long bytesSent = 0;
            long packetsReceived = 0;
            long packetsSent = 0;

            for (NetworkIF nif : networks) {
                nif.updateAttributes();
                bytesReceived += nif.getBytesRecv();
                bytesSent += nif.getBytesSent();
                packetsReceived += nif.getPacketsRecv();
                packetsSent += nif.getPacketsSent();

            }
            this.bytesReceived = bytesReceived;
            this.bytesSent = bytesSent;
            this.packetsReceived = packetsReceived;
            this.packetsSent = packetsSent;

            this.lastRefresh = System.currentTimeMillis();
        }
    }
}
