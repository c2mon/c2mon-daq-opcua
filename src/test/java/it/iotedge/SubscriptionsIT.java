package it.iotedge;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@ExtendWith(EdgeConnectionResolver.class)
public class SubscriptionsIT extends EdgeITBase {

    int TIMEOUT = 6000;
    CompletableFuture<Object> future;

    @BeforeEach
    public void setupEndpoint(Map<String, String> addresses) {
        future = listenForServerResponse(endpoint);
        super.setupEndpoint(addresses);
        endpoint.initialize(false);
        log.info("Client ready");
    }

    @AfterEach
    public void cleanUp() {
        endpoint.reset();
        endpoint = null;
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() {
        CompletableFuture<Object> future = listenForServerResponse(endpoint);

        endpoint.subscribeTag(ServerTagFactory.RandomUnsignedInt32.createDataTag());

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid () throws ExecutionException, InterruptedException {
        endpoint.subscribeTag(ServerTagFactory.Invalid.createDataTag());

        SourceDataTagQuality response = (SourceDataTagQuality) future.get();
        Assertions.assertEquals(SourceDataTagQualityCode.INCORRECT_NATIVE_ADDRESS, response.getQualityCode());
    }

    @Test
    public void subscribeAndSetDeadband() {
        float valueDeadband = 50;
        CompletableFuture<Object> future = listenForServerResponse(publisher, valueDeadband);
        ISourceDataTag dataTag = ServerTagFactory.DipData.createDataTag(valueDeadband, (short) DeadbandType.Absolute.getValue(), 0);

        endpoint.subscribeTag(dataTag);

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void refreshProperTag () {
        CompletableFuture<Object> future = listenForServerResponse(endpoint);

        endpoint.refreshDataTags(Collections.singletonList(ServerTagFactory.RandomUnsignedInt32.createDataTag()));

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    private CompletableFuture<Object> listenForServerResponse(Endpoint endpoint) {
        return listenForServerResponse(publisher, 0.0f);
    }


    private static CompletableFuture<Object> listenForServerResponse(EventPublisher publisher, float valueDeadband) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        publisher.subscribe(new EndpointListener() {
            @Override
            public void update (EquipmentState state) {

            }

            @Override
            public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
                log.info("received: {}, {}", dataTag.getName(), valueUpdate);
                if (approximatelyEqual(dataTag.getValueDeadband(), valueDeadband)) {
                    future.complete(quality);
                } else {
                    future.completeExceptionally(new Throwable("ValueDeadband was not observed by the Server!"));
                }
            }

            @Override
            public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
                log.info("is invalid: {}", dataTag.getName());
                future.complete(quality);
            }

            @Override
            public void initialize (IEquipmentMessageSender sender) {

            }
        });
        return future;
    }

    private static boolean approximatelyEqual(float a, double b)
    {
        return Math.abs(a-b) <= ( (Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math.abs(a)) * 0.1);
    }

}
