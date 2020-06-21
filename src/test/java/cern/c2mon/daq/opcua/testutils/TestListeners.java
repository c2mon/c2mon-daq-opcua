package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Getter
    @Setter
    @Component(value = "pulseTestListener")
    public static class Pulse extends TestListener {
        private long sourceID;
        private int threshold = 0;
        CompletableFuture<ValueUpdate> pulseTagUpdate = new CompletableFuture<>();
        CompletableFuture<ValueUpdate> tagValUpdate = new CompletableFuture<>();

        public Pulse(TagSubscriptionMapper mapper) {
            super(mapper);
        }

        public void reset() {
            super.reset();
            pulseTagUpdate = new CompletableFuture<>();
            tagValUpdate = new CompletableFuture<>();
            threshold = 0;
            sourceID = -1L;
        }

        @Override
        public void onValueUpdate(UInteger clientHandle, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
            super.onValueUpdate(clientHandle, quality, valueUpdate);
            final Long tagId = mapper.getTagId(clientHandle);
            if (tagId != null && tagId.equals(sourceID) && (tagValUpdate.isDone() || thresholdReached(valueUpdate, threshold))) {
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
    @RequiredArgsConstructor
    @Component(value = "testListener")
    public static class TestListener implements EndpointListener, SessionActivityListener {
        @Setter
        boolean debugEnabled = true;
        CompletableFuture<Long> tagUpdate = new CompletableFuture<>();
        CompletableFuture<Long> tagInvalid = new CompletableFuture<>();
        CompletableFuture<EquipmentState> stateUpdate = new CompletableFuture<>();
        CompletableFuture<Void> alive = new CompletableFuture<>();

        @Autowired
        protected final TagSubscriptionMapper mapper;

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
        public void initialize (IEquipmentMessageSender sender) {
        }

        @Override
        public void onTagInvalid(UInteger clientHandle, SourceDataTagQuality quality) {
            if (debugEnabled) {
                log.info("data tag {} invalid with quality {}", mapper.getTagId(clientHandle), quality);
            }
            tagInvalid.complete(mapper.getTagId(clientHandle));
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

        @Override
        public void onValueUpdate(UInteger clientHandle, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
            if (quality.isValid()) {
                final Long tagId = mapper.getTagId(clientHandle);
                if (tagId == null) {
                    log.error("received update for unknown clientHandle {} where mapper is in state {}.", clientHandle, mapper.getTagIdDefinitionMap());
                } else if (debugEnabled) {
                    log.info("received data tag {}, client handle {}, value update {}, quality {}", tagId, clientHandle, valueUpdate, quality);
                }
                tagUpdate.complete(tagId);
            } else {
                onTagInvalid(clientHandle, quality);
            }
        }

        public CompletableFuture<EquipmentState> listen() {
            stateUpdate = new CompletableFuture<>();
            return stateUpdate;
        }

        private void completeAndReset(boolean value) {
            if (debugEnabled) {
                log.info("State update: {}", value ? "connected." : "disconnected");
            }
            if (stateUpdate != null && !stateUpdate.isDone()) {
                stateUpdate.completeAsync(() -> value ? EquipmentState.OK : EquipmentState.CONNECTION_LOST);
            }
        }

    }
}
