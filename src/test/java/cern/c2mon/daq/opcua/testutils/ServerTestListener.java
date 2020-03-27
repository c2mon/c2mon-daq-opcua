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

import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class ServerTestListener {

    public static CompletableFuture<Object> listenForTagResponse(EventPublisher publisher, float valueDeadband) {
        return createListenerAndReturnFuture(publisher, true, valueDeadband);
    }

    public static CompletableFuture<Object> listenForWriteResponse(EventPublisher publisher) {
        return createListenerAndReturnFuture(publisher, false, 0);
    }

    private static CompletableFuture<Object> createListenerAndReturnFuture(EventPublisher publisher, boolean listenForTagUpdates, float valueDeadband) {
        CompletableFuture<Object> tagValueUpdate = new CompletableFuture<>();
        CompletableFuture<Object> writeResponse = new CompletableFuture<>();
        publisher.subscribe(createEndpointListener(tagValueUpdate, writeResponse, valueDeadband));
        return (listenForTagUpdates) ? tagValueUpdate : writeResponse;
    }

    public static EndpointListener createEndpointListener(CompletableFuture<Object> tagValueUpdate, CompletableFuture<Object> writeResponse, float valueDeadband) {
        return new EndpointListener() {
            @Override
            public void update (EquipmentState state) {

            }

            @Override
            public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
                log.info("received: {}, {}", dataTag.getName(), valueUpdate);
                if (approximatelyEqual(dataTag.getValueDeadband(), valueDeadband)) {
                    tagValueUpdate.complete(quality);
                } else {
                    tagValueUpdate.completeExceptionally(new Throwable("ValueDeadband was not observed by the Server!"));
                }
            }

            @Override
            public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
                log.info("is invalid: {}", dataTag.getName());
                tagValueUpdate.complete(quality);
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
