package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloMapper;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
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
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static org.easymock.EasyMock.createMock;

@Getter
@Setter
@RequiredArgsConstructor
@Component(value = "testEndpoint")
public class TestEndpoint implements Endpoint {
    private final MessageSender messageSender;
    private final TagSubscriptionReader mapper;
    UaMonitoredItem monitoredItem = createMock(UaMonitoredItem.class);
    UaSubscription subscription = createMock(UaSubscription.class);
    NonTransparentRedundancyTypeNode serverRedundancyTypeNode = createMock(NonTransparentRedundancyTypeNode.class);
    private boolean returnGoodStatusCodes = true;
    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    String uri;
    boolean throwExceptions = false;
    long delay;

    @Override
    public void initialize(String uri) throws OPCUAException {
        this.uri = uri;
        this.delay = 0;
        if (throwExceptions) {
            throw new CommunicationException(CONNECT);
        }
    }

    @Override
    public void manageSessionActivityListener(boolean add, SessionActivityListener listener) {

    }

    @Override
    public void setUpdateEquipmentStateOnSessionChanges(boolean active) {

    }

    @Override
    public void disconnect () {}

    @Override
    public Map<Integer, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        Consumer<UaMonitoredItem> itemCreationCallback = item -> item.setValueConsumer(value -> {
            SourceDataTagQuality tagQuality = MiloMapper.getDataTagQuality((value.getStatusCode() == null) ? StatusCode.BAD : value.getStatusCode());
            final Object updateValue = MiloMapper.toObject(value.getValue());
            ValueUpdate valueUpdate = (value.getSourceTime() == null) ?
                    new ValueUpdate(updateValue) :
                    new ValueUpdate(updateValue, value.getSourceTime().getJavaTime());
            final Long tagId = mapper.getTagId(item.getClientHandle().intValue());
            messageSender.onValueUpdate(tagId, tagQuality, valueUpdate);
        });
        return subscribeWithCallback(group.getPublishInterval(), definitions,  itemCreationCallback);
    }

    @Override
    public Map<Integer, SourceDataTagQuality> subscribeWithCallback(int publishingInterval, Collection<ItemDefinition> definitions, Consumer<UaMonitoredItem> itemCreationCallback) {
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.schedule(() -> itemCreationCallback.accept(monitoredItem), delay + 100, TimeUnit.MILLISECONDS);

        return  definitions.stream().collect(Collectors.toMap(ItemDefinition::getClientHandle, c -> {
            final StatusCode statusCode = monitoredItem.getStatusCode();
            return MiloMapper.getDataTagQuality(statusCode == null ? StatusCode.BAD : statusCode);
        }));
    }

    @Override
    public boolean deleteItemFromSubscription(int clientHandle, int publishInterval) {
        return returnGoodStatusCodes;
    }

    @Override
    public void recreateAllSubscriptions() {

    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws CommunicationException {
        if (throwExceptions) {
            throw new CommunicationException(READ);
        }
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final SourceDataTagQuality quality = new SourceDataTagQuality(returnGoodStatusCodes ? SourceDataTagQualityCode.OK : SourceDataTagQualityCode.VALUE_CORRUPTED);
        return new AbstractMap.SimpleEntry<>(new ValueUpdate(0), quality);
    }

    @Override
    public boolean write (NodeId nodeId, Object value) throws CommunicationException {
        if (throwExceptions) {
            throw new CommunicationException(WRITE);
        }
        return returnGoodStatusCodes;
    }

    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) {
        return new AbstractMap.SimpleEntry<>(returnGoodStatusCodes, new Object[] {arg});
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() {
        return serverRedundancyTypeNode;
    }

}

