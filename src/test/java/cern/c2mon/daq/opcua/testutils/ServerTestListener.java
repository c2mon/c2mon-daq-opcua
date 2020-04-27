package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.EventPublisher;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.*;
import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.CONNECTION_LOST;

@Slf4j
public abstract class ServerTestListener {

    public enum Target {TAG_UPDATE, TAG_INVALID, WRITE_RESPONSE, EQUIPMENT_STATE}

    public static Map<Target, CompletableFuture<Object>> createListenerAndReturnFutures(EventPublisher publisher) {
        final Map<Target, CompletableFuture<Object>> futures = new HashMap<>();
        futures.put(TAG_UPDATE, new CompletableFuture<>());
        futures.put(TAG_INVALID, new CompletableFuture<>());
        futures.put(WRITE_RESPONSE, new CompletableFuture<>());
        futures.put(EQUIPMENT_STATE, new CompletableFuture<>());
        publisher.subscribe(createEndpointListener(
                futures.get(TAG_UPDATE),
                futures.get(TAG_INVALID),
                futures.get(WRITE_RESPONSE),
                futures.get(EQUIPMENT_STATE)));
        return futures;
    }

    public static EndpointListener createEndpointListener(CompletableFuture<Object> tagValueUpdate,
                                                          CompletableFuture<Object> tagInvalidFuture,
                                                          CompletableFuture<Object> writeResponse,
                                                          CompletableFuture<Object> stateUpdate) {

        final ArrayList<EndpointListener.EquipmentState> states = new ArrayList<>();
        return new EndpointListener() {
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
                tagValueUpdate.complete(dataTag);
            }

            @Override
            public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
                log.info("data tag {} invalid with quality {}", dataTag.getName(), quality);
                tagInvalidFuture.complete(dataTag);
            }

            @Override
            public void onWriteResponse(StatusCode statusCode, ISourceCommandTag tag) {
                log.info("write response for {} with status code {}", tag.getName(), statusCode);
                writeResponse.complete(statusCode);
            }

            @Override
            public void initialize (IEquipmentMessageSender sender) {

            }
        };
    }
}
