package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.*;
import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.CONNECTION_LOST;

@Slf4j
public abstract class ServerTestListener {

    public enum Target {TAG_UPDATE, TAG_INVALID, COMMAND_RESPONSE, METHOD_RESPONSE, EQUIPMENT_STATE, ALIVE}


    private static float valueUpdateToFloat(ValueUpdate valueUpdate) {
        final Object t = valueUpdate.getValue();
        return (t instanceof Float) ? (float) t : (int) t;
    }

    static boolean thresholdReached(ValueUpdate valueUpdate, int threshold) {
        return Math.abs(valueUpdateToFloat(valueUpdate) - threshold) < 0.5;
    }

    public static Map<Target, CompletableFuture<?>> createListenerAndReturnFutures(EventPublisher publisher) {
        final TestListener listener = new TestListener();
        publisher.subscribe(listener);

        final Map<Target, CompletableFuture<?>> futures = new HashMap<>();
        futures.put(TAG_UPDATE, listener.getTagUpdate());
        futures.put(TAG_INVALID, listener.getTagInvalid());
        futures.put(COMMAND_RESPONSE, listener.getCommandResponse());
        futures.put(METHOD_RESPONSE, listener.getMethodResponse());
        futures.put(EQUIPMENT_STATE, listener.getStateUpdate());
        futures.put(ALIVE, listener.getAlive());
        return futures;
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    public static class PulseTestListener extends TestListener {
        private final long sourceID;
        private final long commandID;
        private int threshold = 0;
        CompletableFuture<ValueUpdate> pulseTagUpdate = new CompletableFuture<>();
        CompletableFuture<ValueUpdate> tagValUpdate = new CompletableFuture<>();

        @Override
        public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            if (dataTag.getId().equals(sourceID) && (threshold == 0 || thresholdReached(valueUpdate, threshold))) {
                if (tagValUpdate.isDone()) {
                    log.info("completing pulseTagUpdate");
                    pulseTagUpdate.complete(valueUpdate);
                } else {
                    log.info("completing tagUpdate");
                    tagValUpdate.complete(valueUpdate);
                    threshold = 0;
                }
            }
        }

        @Override
        public void onCommandResponse(StatusCode statusCode, ISourceCommandTag tag) {
            if (tag.getId().equals(commandID)) {
                log.info("onCommandResponse: dataTag {}, quality {}", tag, statusCode);
                commandResponse.complete(statusCode);
            }
        }
    }

    @Getter
    @NoArgsConstructor
    public static class TestListener implements EndpointListener {
        final ArrayList<EndpointListener.EquipmentState> states = new ArrayList<>();
        CompletableFuture<ISourceDataTag> tagUpdate = new CompletableFuture<>();
        CompletableFuture<ISourceDataTag> tagInvalid = new CompletableFuture<>();
        CompletableFuture<StatusCode> commandResponse = new CompletableFuture<>();
        CompletableFuture<Map.Entry<StatusCode, Object[]>> methodResponse = new CompletableFuture<>();
        CompletableFuture<List<EquipmentState>> stateUpdate = new CompletableFuture<>();
        CompletableFuture<StatusCode> alive = new CompletableFuture<>();

        @Override
        public void update (EquipmentState state) {
            states.add(state);
            if (!state.equals(CONNECTION_LOST)) {
                stateUpdate.complete(states);
            }
        }

        @Override
        public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            log.info("received data tag {}, value update {}, quality {}", dataTag.getName(), valueUpdate, quality);
            tagUpdate.complete(dataTag);
        }

        @Override
        public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
            log.info("data tag {} invalid with quality {}", dataTag.getName(), quality);
            tagInvalid.complete(dataTag);
        }

        @Override
        public void onMethodResponse(StatusCode statusCode, Object[] results, ISourceCommandTag tag) {
            log.info("Method response for {} with status code {} and result {} ", tag.getName(), statusCode, results);
            methodResponse.complete(Map.entry(statusCode, results));
        }

        @Override
        public void onCommandResponse(StatusCode statusCode, ISourceCommandTag tag) {
            log.info("write response for {} with status code {}", tag.getName(), statusCode);
            commandResponse.complete(statusCode);

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
