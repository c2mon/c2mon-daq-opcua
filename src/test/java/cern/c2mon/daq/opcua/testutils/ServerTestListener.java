package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.*;
import static cern.c2mon.daq.opcua.testutils.ServerTestListener.Target.TAG_INVALID;
import static cern.c2mon.daq.opcua.upstream.EndpointListener.EquipmentState.CONNECTION_LOST;

@Slf4j
public abstract class ServerTestListener {

    public enum Target {TAG_UPDATE, TAG_INVALID, WRITE_RESPONSE, EQUIPMENT_STATE}

    public static CompletableFuture<Object> listenForTagUpdate(EventPublisher publisher, float valueDeadband) {
        return createListenerAndReturnFutures(publisher, valueDeadband).get(TAG_UPDATE);
    }

    public static CompletableFuture<Object> listenForTagInvalid(EventPublisher publisher) {
        return createListenerAndReturnFutures(publisher, null).get(TAG_INVALID);
    }

    public static CompletableFuture<Object> listenForWriteResponse(EventPublisher publisher) {
        return createListenerAndReturnFutures(publisher, null).get(WRITE_RESPONSE);

    }
    public static CompletableFuture<Object> listenForEquipmentState(EventPublisher publisher) {
        return createListenerAndReturnFutures(publisher, null).get(EQUIPMENT_STATE);

    }

    public static Map<Target, CompletableFuture<Object>> createListenerAndReturnFutures(EventPublisher publisher, Float valueDeadband) {
        final Map<Target, CompletableFuture<Object>> futures = new HashMap<>();
        futures.put(TAG_UPDATE, new CompletableFuture<>());
        futures.put(TAG_INVALID, new CompletableFuture<>());
        futures.put(WRITE_RESPONSE, new CompletableFuture<>());
        futures.put(EQUIPMENT_STATE, new CompletableFuture<>());
        publisher.subscribe(createEndpointListener(futures.get(TAG_UPDATE),
                futures.get(TAG_INVALID),
                futures.get(WRITE_RESPONSE),
                futures.get(EQUIPMENT_STATE),
                new ArrayList<>(),
                valueDeadband));
        return futures;
    }

    public static EndpointListener createEndpointListener(CompletableFuture<Object> tagValueUpdate,
                                                          CompletableFuture<Object> tagInvalidFuture,
                                                          CompletableFuture<Object> writeResponse,
                                                          CompletableFuture<Object> stateUpdate,
                                                          List<EndpointListener.EquipmentState> states,
                                                          Float valueDeadband) {
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
                log.info("received: {}, {}", dataTag.getName(), valueUpdate);
                if (valueDeadband != null && !approximatelyEqual(dataTag.getValueDeadband(), valueDeadband)) {
                    tagValueUpdate.completeExceptionally(new Throwable("ValueDeadband was not observed by the Server!"));
                } else {
                    tagValueUpdate.complete(dataTag);
                }
            }

            @Override
            public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
                log.info("is invalid: {}", dataTag.getName());
                tagInvalidFuture.complete(dataTag);
            }

            @Override
            public void onWriteResponse(StatusCode statusCode, ISourceCommandTag tag) {
                writeResponse.complete(statusCode);
            }

            @Override
            public void initialize (IEquipmentMessageSender sender) {

            }
        };
    }

    private static boolean approximatelyEqual(float a, double b)
    {
        return Math.abs(a-b) <= ( (Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math.abs(a)) * 0.1);
    }
}
