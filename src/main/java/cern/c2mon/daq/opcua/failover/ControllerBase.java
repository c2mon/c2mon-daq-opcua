package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class ControllerBase implements Controller {

    protected static UByte SERVICE_LEVEL_HEALTH_LIMIT = UByte.valueOf(200);
    protected static List<ItemDefinition> connectionMonitoringNodes = List.of(
            ItemDefinition.of(Identifiers.Server_ServiceLevel),
            ItemDefinition.of(Identifiers.ServerState));
    protected static final AtomicBoolean listening = new AtomicBoolean(true);

    /** A flag indicating whether the controller was stopped. */
    protected final AtomicBoolean stopped = new AtomicBoolean(true);

    protected final TagSubscriptionMapper mapper;
    protected final MessageSender messageSender;
    protected final RetryDelegate retryDelegate;
    private final ApplicationContext applicationContext;

    public void initializeMonitoring(String uri, Endpoint endpoint, String[] redundantAddresses) throws OPCUAException {
        stopped.set(false);
    }

    public void stop() {
        log.info("Disconnecting... ");
        stopped.set(true);
        mapper.clear();
        final Collection<Endpoint> endpoints = new ArrayList<>(passiveEndpoints());
        endpoints.add(currentEndpoint());
        for (var e : endpoints) {
            try {
                e.disconnect();
            } catch (OPCUAException ex) {
                log.error("Error disconnecting from endpoint with uri {}: ", e.getUri(), ex);
            }
        }
    }
    @Override
    public Map<UInteger, SourceDataTagQuality>subscribe(Collection<ItemDefinition> definitions) {
        final Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions = mapper.mapToGroups(definitions);
        return groupsWithDefinitions.entrySet().stream()
                .flatMap(e -> subscribeGroup(e.getKey(), e.getValue()).entrySet().stream())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public void unsubscribe(ItemDefinition definition) throws OPCUAException {
        passiveEndpoints().forEach(e -> {
            try {
                e.deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband());
            } catch (OPCUAException ex) {
                log.error("Tag with ID {} could not be completed successfully on endpoint {}.", mapper.getTagId(definition), e.getUri());
            }
        });
        currentEndpoint().deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband());
    }

    protected Map<UInteger, SourceDataTagQuality> subscribeGroup(SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        passiveEndpoints().forEach(e -> subscribeOnEndpoint(e, group, definitions));
        final Map<UInteger, SourceDataTagQuality> tagQualityMap = subscribeOnEndpoint(currentEndpoint(), group, definitions);
        return (tagQualityMap != null) ? tagQualityMap: Collections.emptyMap();
    }

    protected Map<UInteger, SourceDataTagQuality> subscribeOnEndpoint(Endpoint endpoint, SubscriptionGroup group, Collection<ItemDefinition> definitions) {
        try {
            return endpoint.subscribeToGroup(group.getPublishInterval(), definitions);
        } catch (OPCUAException ignored) {
            final String ids = Joiner.on(", ").join(definitions.stream().map(mapper::getTagId).toArray());
            log.error("Tags with IDs {} could not be subscribed on endpoint with uri {}. ", ids, endpoint.getUri());
        }
        return null;
    }

    protected Endpoint nextEndpoint() {
        return applicationContext.getBean(Endpoint.class);
    }

    public void triggerServerSwitch() {
        log.info("switchServer");
        if (listening.getAndSet(false)) {
            currentEndpoint().monitorEquipmentState(false, (SessionActivityListener) messageSender);
            try {
                retryDelegate.triggerServerSwitchRetry(this);
                listening.set(true);
            } catch (OPCUAException e) {
                //should not happen - only after Integer.MAX_VALUE failures
                triggerServerSwitch();
            }
        }
    }

    protected void monitoringCallback(UaMonitoredItem item, Integer integer) {
        final var nodeId = item.getReadValueId().getNodeId();
        if (nodeId.equals(Identifiers.Server_ServiceLevel)) {
            item.setValueConsumer(this::serviceLevelConsumer);
        } else if (nodeId.equals(Identifiers.ServerState)) {
            item.setValueConsumer(this::serverStateConsumer);
        }
    }

    protected void serviceLevelConsumer (DataValue value) {
        final var serviceLevel = (UByte) value.getValue().getValue();
        if (serviceLevel.compareTo(SERVICE_LEVEL_HEALTH_LIMIT) < 0) {
            log.info("Service level is outside of the healthy range at {}. Switching server...", serviceLevel.intValue());
            triggerServerSwitch();
        }
    }

    protected void serverStateConsumer (DataValue value) {
        final var state = (ServerState) value.getValue().getValue();
        if (!state.equals(ServerState.Running) && !state.equals(ServerState.Unknown)) {
            log.info("Server entered state {}. Switching server...", state);
            triggerServerSwitch();
        }
    }

    public abstract void switchServers() throws OPCUAException;

}
