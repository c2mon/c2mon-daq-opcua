package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.stereotype.Component;

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

    @RequiredArgsConstructor
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
        public void onNewTagValue (Long dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            super.onNewTagValue(dataTag, valueUpdate, quality);
            if (dataTag.equals(sourceID) && (tagValUpdate.isDone() || thresholdReached(valueUpdate, threshold))) {
                if (tagValUpdate.isDone()) {
                    if (debugEnabled) {
                        log.info("completing pulseTagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    }
                    pulseTagUpdate.complete(valueUpdate);
                } else {
                    if (debugEnabled) {
                        log.info("completing tagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    }
                    tagValUpdate.complete(valueUpdate);
                }
            }
        }
    }

    @Getter
    @NoArgsConstructor
    public static class TestListener implements EndpointListener, SessionActivityListener {
        @Setter
        boolean debugEnabled = true;
        CompletableFuture<Long> tagUpdate = new CompletableFuture<>();
        CompletableFuture<Long> tagInvalid = new CompletableFuture<>();
        CompletableFuture<EquipmentState> stateUpdate = new CompletableFuture<>();
        CompletableFuture<StatusCode> alive = new CompletableFuture<>();

        public void reset() {
            tagUpdate = new CompletableFuture<>();
            tagInvalid = new CompletableFuture<>();
            stateUpdate = new CompletableFuture<>();
            alive = new CompletableFuture<>();
        }

        @Override
        public void onNewTagValue (Long dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            if (debugEnabled) {
                log.info("received data tag {}, value update {}, quality {}", dataTag, valueUpdate, quality);
            }
            tagUpdate.complete(dataTag);
        }

        @Override
        public void onTagInvalid (Long dataTag, SourceDataTagQuality quality) {
            if (debugEnabled) {
                log.info("data tag {} invalid with quality {}", dataTag, quality);
            }
            tagInvalid.complete(dataTag);
        }

        @Override
        public void onAlive(StatusCode statusCode) {
            alive.complete(statusCode);
        }


        @Override
        public void initialize (IEquipmentMessageSender sender) {
        }

        @Override
        public void onSessionActive(UaSession session) {
            completeAndReset(true);
        }

        @Override
        public void onSessionInactive(UaSession session) {
            completeAndReset(false);
        }

        @Override
        public void onEquipmentStateUpdate(EquipmentState state) {
            stateUpdate.complete(state);
        }

        public CompletableFuture<EquipmentState> listen() {
            stateUpdate = new CompletableFuture<>();
            return stateUpdate;
        }

        private void completeAndReset(boolean value) {
            if (stateUpdate != null && !stateUpdate.isDone()) {
                stateUpdate.completeAsync(() -> value ? EquipmentState.OK : EquipmentState.CONNECTION_LOST);
            }
        }

    }
}
