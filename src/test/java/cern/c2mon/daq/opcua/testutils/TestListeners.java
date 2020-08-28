package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            }
        }
    }

    @Getter
    public static class TestListener implements MessageSender {
        List<CompletableFuture<Long>> tagUpdate = new ArrayList<>();
        List<CompletableFuture<Long>> tagInvalid = new ArrayList<>();
        List<CompletableFuture<EquipmentState>> stateUpdate = new ArrayList<>();
        List<CompletableFuture<Void>> alive = new ArrayList<>();

        @Setter
        boolean logging = true;

        public TestListener() {
            tagUpdate.add(new CompletableFuture<>());
            tagInvalid.add(new CompletableFuture<>());
            stateUpdate.add(new CompletableFuture<>());
            alive.add(new CompletableFuture<>());
        }

        public void reset() {
            tagUpdate.clear();
            tagInvalid.clear();
            stateUpdate.clear();
            alive.clear();
            tagUpdate.add(new CompletableFuture<>());
            tagInvalid.add(new CompletableFuture<>());
            stateUpdate.add(new CompletableFuture<>());
            alive.add(new CompletableFuture<>());
        }

        @Override
        public void onAlive() {
            alive.get(0).complete(null);
            alive.add(0, new CompletableFuture<>());
        }


        @Override
        public void initialize(IEquipmentMessageSender sender) {
        }

        @Override
        public void onTagInvalid(long tagId, SourceDataTagQuality quality) {
            if (logging) {
                log.info("Received data tag {} invalid with quality {}", tagId, quality);
            }
            tagInvalid.get(0).complete(tagId);
            tagInvalid.add(0, new CompletableFuture<>());
        }

        @Override
        public void onEquipmentStateUpdate(EquipmentState state) {
            if (logging) {
                log.info("State update: {}", state);
            }
            stateUpdate.get(0).complete(state);
            stateUpdate.add(0, new CompletableFuture<>());
        }

        @Override
        public void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
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

        public synchronized CompletableFuture<EquipmentState> listen() {
            CompletableFuture<EquipmentState> stateFuture = new CompletableFuture<>();
            stateUpdate.add(0, stateFuture);
            return stateFuture;
        }
    }
}