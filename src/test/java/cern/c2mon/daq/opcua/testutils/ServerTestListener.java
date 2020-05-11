package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.CONNECTION_LOST;

@Slf4j
public abstract class ServerTestListener {

    private static float valueUpdateToFloat(ValueUpdate valueUpdate) {
        final Object t = valueUpdate.getValue();
        return (t instanceof Float) ? (float) t : (int) t;
    }

    static boolean thresholdReached(ValueUpdate valueUpdate, int threshold) {
        return Math.abs(valueUpdateToFloat(valueUpdate) - threshold) < 0.5;
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    @Component(value = "pulseTestListener")
    public static class PulseTestListener extends TestListener {
        private long sourceID;
        private int threshold = 0;
        CompletableFuture<ValueUpdate> pulseTagUpdate = new CompletableFuture<>();
        CompletableFuture<ValueUpdate> tagValUpdate = new CompletableFuture<>();

        @Override
        public void onNewTagValue (Long dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            super.onNewTagValue(dataTag, valueUpdate, quality);
            if (dataTag.equals(sourceID) && (threshold == 0 || thresholdReached(valueUpdate, threshold))) {
                if (tagValUpdate.isDone()) {
                    log.info("completing pulseTagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    pulseTagUpdate.complete(valueUpdate);
                } else {
                    log.info("completing tagUpdate on tag with ID {} with value {}", sourceID, valueUpdate.getValue());
                    tagValUpdate.complete(valueUpdate);
                    threshold = 0;
                }
            }
        }
    }

    @Getter
    @NoArgsConstructor
    public static class TestListener implements EndpointListener {
        final ArrayList<EndpointListener.EquipmentState> states = new ArrayList<>();
        CompletableFuture<Long> tagUpdate = new CompletableFuture<>();
        CompletableFuture<Long> tagInvalid = new CompletableFuture<>();
        CompletableFuture<List<EquipmentState>> stateUpdate = new CompletableFuture<>();
        CompletableFuture<StatusCode> alive = new CompletableFuture<>();

        @Override
        public void onEquipmentStateUpdate(EquipmentState state) {
            states.add(state);
            if (!state.equals(CONNECTION_LOST)) {
                stateUpdate.complete(states);
            }
        }

        @Override
        public void onNewTagValue (Long dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            log.info("received data tag {}, value update {}, quality {}", dataTag, valueUpdate, quality);
            tagUpdate.complete(dataTag);
        }

        @Override
        public void onTagInvalid (Long dataTag, SourceDataTagQuality quality) {
            log.info("data tag {} invalid with quality {}", dataTag, quality);
            tagInvalid.complete(dataTag);
        }

        @Override
        public void onAlive(StatusCode statusCode) {
            alive.complete(statusCode);
        }

        @Override
        public void initialize (IEquipmentMessageSender sender) {

        }
    }
}
