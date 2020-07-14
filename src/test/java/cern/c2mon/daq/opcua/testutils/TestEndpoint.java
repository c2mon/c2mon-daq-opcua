package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.mapping.*;
import cern.c2mon.daq.opcua.tagHandling.MessageSender;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.easymock.EasyMock.createMock;

@Getter
@Setter
@RequiredArgsConstructor
@Component(value = "testEndpoint")
public class TestEndpoint implements Endpoint {
    private final MessageSender messageSender;
    private final TagSubscriptionMapReader mapper;
    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);
    NonTransparentRedundancyTypeNode serverRedundancyTypeNode = createMock(NonTransparentRedundancyTypeNode.class);
    private boolean returnGoodStatusCodes = true;
    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    String uri;

    @Override
    public void initialize(String uri) throws OPCUAException {
        this.uri = uri;
    }

    @Override
    public void manageSessionActivityListener(boolean add, SessionActivityListener listener) {

    }

    @Override
    public void disconnect () {}

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        BiConsumer<UaMonitoredItem, Integer> itemCreationCallback = (item, i) -> item.setValueConsumer(value -> {
            SourceDataTagQuality tagQuality = MiloMapper.getDataTagQuality((value.getStatusCode() == null) ? StatusCode.BAD : value.getStatusCode());
            final Object updateValue = MiloMapper.toObject(value.getValue());
            ValueUpdate valueUpdate = (value.getSourceTime() == null) ?
                    new ValueUpdate(updateValue) :
                    new ValueUpdate(updateValue, value.getSourceTime().getJavaTime());
            final Optional<Long> tagId = mapper.getTagId(item.getClientHandle());
            messageSender.onValueUpdate(tagId, tagQuality, valueUpdate);
        });
        executor.schedule(() -> itemCreationCallback.accept(monitoredItem, 1), 100, TimeUnit.MILLISECONDS);

        return  definitions.stream().collect(Collectors.toMap(ItemDefinition::getClientHandle, c -> {
            final StatusCode statusCode = monitoredItem.getStatusCode();
            return MiloMapper.getDataTagQuality(statusCode == null ? StatusCode.BAD : statusCode);
        }));
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribeWithCallback(int publishingInterval, Collection<ItemDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCUAException {
        return null;
    }

    @Override
    public boolean deleteItemFromSubscription(UInteger clientHandle, int publishInterval) {
        return true;
    }

    @Override
    public void recreateSubscription(UaSubscription subscription) {

    }

    @Override
    public void recreateAllSubscriptions() {

    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        final var quality = new SourceDataTagQuality(returnGoodStatusCodes ? SourceDataTagQualityCode.OK : SourceDataTagQualityCode.VALUE_CORRUPTED);
        return Map.entry(new ValueUpdate(0), quality);
    }

    @Override
    public boolean write (NodeId nodeId, Object value) throws CommunicationException {
        return returnGoodStatusCodes;
    }

    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return Map.entry(returnGoodStatusCodes, new Object[] {arg});
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() {
        return serverRedundancyTypeNode;
    }

}

