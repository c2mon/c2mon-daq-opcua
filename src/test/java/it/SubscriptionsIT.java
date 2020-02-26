package it;

import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class SubscriptionsIT extends OpcUaInfrastructureBase {

    int TIMEOUT = 3000;

    @BeforeEach
    public void setUp() {
        endpoint.initialize(address);
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() {
        CompletableFuture<Object> future = listenForServerResponse(endpoint);

        endpoint.subscribeTag(ServerTagProvider.RandomUnsignedInt32.createDataTag());

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnNothing() {
        //TODO currently subscribing invalid dataTags created an empty subscription -> should this be so?

        CompletableFuture<Object> future = listenForServerResponse(endpoint);

        endpoint.subscribeTag(ServerTagProvider.Invalid.createDataTag());

        Assertions.assertThrows(TimeoutException.class, () -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeAndSetDeadband() {
        float valueDeadband = 50;
        CompletableFuture<Object> future = listenForServerResponse(endpoint, valueDeadband);
        ISourceDataTag dataTag = ServerTagProvider.DipData.createDataTag(valueDeadband, (short) DeadbandType.Absolute.getValue(), 0);

        endpoint.subscribeTag(dataTag);

        Object o = Assertions.assertDoesNotThrow(() -> future.get(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(o);
    }

    private CompletableFuture<Object> listenForServerResponse(EndpointImpl endpoint) {
        return listenForServerResponse(endpoint, 0.0f);
    }

    private CompletableFuture<Object> listenForServerResponse(EndpointImpl endpoint, float valueDeadband) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        endpoint.registerEndpointListener(new EndpointListener() {
            @Override
            public void onNewTagValue(ISourceDataTag dataTag, long timestamp, Object value) {
                log.info("received: {}, {}", dataTag.getName(), value);
                if (approximatelyEqual(dataTag.getValueDeadband(), valueDeadband)) {
                    future.complete(value);
                } else {
                    future.completeExceptionally(new Throwable("ValueDeadband was not observed by the Server!"));
                }
            }

            @Override
            public void onTagInvalidException(ISourceDataTag dataTag, Throwable cause) {
                log.info("invalid: {}, {}", dataTag.getName(), cause);
                future.completeExceptionally(cause);
            }

            @Override
            public void onSubscriptionException(Throwable cause) {
                log.error("onSubscriptionException ", cause);
                future.completeExceptionally(cause);
            }
        });
        return future;
    }

    private boolean approximatelyEqual(float a, double b)
    {
        return Math.abs(a-b) <= ( (Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math.abs(a)) * 0.1);
    }

}
