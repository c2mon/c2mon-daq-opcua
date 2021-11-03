/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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
package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

@Slf4j
public abstract class TestListeners {

    private static float valueUpdateToFloat(ValueUpdate valueUpdate) {
        final Object t = valueUpdate.getValue();
        return (t instanceof Float) ? (float) t : (int) t;
    }

    static boolean thresholdReached(ValueUpdate valueUpdate, int threshold) {
        return threshold == 0 || Math.abs(valueUpdateToFloat(valueUpdate) - threshold) < 0.5;
    }

    @Getter
    public static class Pulse extends TestListener {
        @Setter
        private long sourceID;
        @Setter
        private int threshold = 0;
        CompletableFuture<ValueUpdate> pulseTagUpdate = new CompletableFuture<>();
        CompletableFuture<ValueUpdate> tagValUpdate = new CompletableFuture<>();

        public void reset() {
            super.reset();
            pulseTagUpdate = new CompletableFuture<>();
            tagValUpdate = new CompletableFuture<>();
            threshold = 0;
            sourceID = -1L;
        }

        @Override
        public void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
            super.onValueUpdate(tagId, quality, valueUpdate);
            if (tagId == sourceID && (tagValUpdate.isDone() || thresholdReached(valueUpdate, threshold))) {
                if (tagValUpdate.isDone()) {
                    if (logging) {
                        log.info("completing pulseTagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    }
                    pulseTagUpdate.complete(valueUpdate);
                } else {
                    if (logging) {
                        log.info("completing tagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    }
                    tagValUpdate.complete(valueUpdate);
                }
            } else if (tagId != sourceID) {
                log.info("Received a value from Tag with ID {}. Ignoring it, since sourceID is set to {}.", tagId, sourceID);
            }
        }
    }

    @Getter
    public static class TestListener implements MessageSender {
        List<CompletableFuture<Long>> tagUpdate = new ArrayList<>();
        List<CompletableFuture<Long>> tagInvalid = new ArrayList<>();
        List<CompletableFuture<EquipmentState>> stateUpdate = new ArrayList<>();

        @Setter
        CountDownLatch aliveLatch;

        @Setter
        boolean logging = true;

        public TestListener() {
            tagUpdate.add(new CompletableFuture<>());
            tagInvalid.add(new CompletableFuture<>());
            stateUpdate.add(new CompletableFuture<>());
            aliveLatch = new CountDownLatch(1);
        }

        public synchronized void reset() {
            tagUpdate.clear();
            tagInvalid.clear();
            stateUpdate.clear();
            tagUpdate.add(new CompletableFuture<>());
            tagInvalid.add(new CompletableFuture<>());
            stateUpdate.add(new CompletableFuture<>());
            aliveLatch = new CountDownLatch(1);
        }

        @Override
        public synchronized void onAlive() {
            aliveLatch.countDown();
        }


        @Override
        public void initialize(IEquipmentMessageSender sender) {
        }

        @Override
        public synchronized void onTagInvalid(long tagId, SourceDataTagQuality quality) {
            if (logging) {
                log.info("Received data tag {} invalid with quality {}", tagId, quality);
            }
            tagInvalid.get(0).complete(tagId);
            tagInvalid.add(0, new CompletableFuture<>());
        }

        @Override
        public synchronized void onEquipmentStateUpdate(EquipmentState state) {
            if (logging) {
                log.info("State update: {}", state);
            }
            stateUpdate.get(0).complete(state);
            stateUpdate.add(0, new CompletableFuture<>());
        }

        @Override
        public synchronized void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
            if (quality.isValid()) {
                if (logging) {
                    log.info("received data tag {}, value update {}, quality {}", tagId, valueUpdate, quality);
                }
                tagUpdate.get(0).complete(tagId);
                tagUpdate.add(0, new CompletableFuture<>());
            } else {
                onTagInvalid(tagId, quality);
            }
        }
    }
}
