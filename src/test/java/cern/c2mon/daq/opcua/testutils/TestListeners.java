package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.tagHandling.IMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
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
    @Setter
    @Component(value = "pulseTestListener")
    public static class Pulse extends TestListener {
        private long sourceID;
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
        public void onValueUpdate(Optional<Long> tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
            super.onValueUpdate(tagId, quality, valueUpdate);
            if (tagId.isPresent() && tagId.get().equals(sourceID) && (tagValUpdate.isDone() || thresholdReached(valueUpdate, threshold))) {
                if (tagValUpdate.isDone()) {
                    log.info("completing pulseTagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    pulseTagUpdate.complete(valueUpdate);
                } else {
                    log.info("completing tagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    tagValUpdate.complete(valueUpdate);
                }
            }
        }
    }

    @Getter
    @RequiredArgsConstructor
    @Component(value = "testListener")
    public static class TestListener implements IMessageSender, SessionActivityListener {
        CompletableFuture<Long> tagUpdate = new CompletableFuture<>();
        CompletableFuture<Long> tagInvalid = new CompletableFuture<>();
        CompletableFuture<EquipmentState> stateUpdate = new CompletableFuture<>();
        CompletableFuture<Void> alive = new CompletableFuture<>();

        public void reset() {
            tagUpdate = new CompletableFuture<>();
            tagInvalid = new CompletableFuture<>();
            stateUpdate = new CompletableFuture<>();
            alive = new CompletableFuture<>();
        }

        @Override
        public void onAlive() {
            alive.complete(null);
        }


        @Override
        public void initialize(IEquipmentMessageSender sender) {
        }

        @Override
        public void onTagInvalid(Optional<Long> tagId, SourceDataTagQuality quality) {
            if (tagId.isPresent()) {
                log.info("Received data tag {} invalid with quality {}", tagId.get(), quality);
                tagInvalid.complete(tagId.get());
            } else {
                log.info("Attempting to invalidate tag with unknown ID");
            }
        }

        @Override
        public void onSessionActive(UaSession session) {
            onEquipmentStateUpdate(EquipmentState.OK);
        }

        @Override
        public void onSessionInactive(UaSession session) {
            onEquipmentStateUpdate(EquipmentState.CONNECTION_LOST);
        }

        @Override
        public void onEquipmentStateUpdate(EquipmentState state) {
            log.info("State update: {}", state);
            if (stateUpdate != null && !stateUpdate.isDone()) {
                stateUpdate.complete(state);
            }
        }

        @Override
        public void onValueUpdate(Optional<Long> tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
            if (quality.isValid() && tagId.isPresent()) {
                log.info("received data tag {}, value update {}, quality {}", tagId.get(), valueUpdate, quality);
                tagUpdate.complete(tagId.get());
            } else if (quality.isValid()) {
                log.error("received update for unknown tagId.");
            } else {
                onTagInvalid(tagId, quality);
            }
        }

        public synchronized CompletableFuture<EquipmentState> listen() {
            stateUpdate = new CompletableFuture<>();
            return stateUpdate;
        }
    }
}