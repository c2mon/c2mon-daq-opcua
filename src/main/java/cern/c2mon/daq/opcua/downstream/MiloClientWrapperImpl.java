package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Slf4j
public class MiloClientWrapperImpl implements MiloClientWrapper {

    private String uri;
    private SecurityPolicy sp;
    private OpcUaClient client;

    public MiloClientWrapperImpl (String uri, SecurityPolicy sp) {
        this.uri = uri;
        this.sp = sp;
    }

    public void initialize () throws ExecutionException, InterruptedException {
        client = MiloClientWrapperImpl.createClient(uri, sp)
                .get();
    }

    public void connect() {
        client.connect();
    }

    public void addEndpointSubscriptionListener(EndpointSubscriptionListener listener) {
        client.getSubscriptionManager().addSubscriptionListener(listener);
    }

    public void disconnect() {
        client.disconnect();
    }

    /**
     *
     * @param timeDeadband The subscription's publishing interval in milliseconds.
     *                     If 0, the Server will use the fastest supported interval
     * @return
     */
    public UaSubscription createSubscription(int timeDeadband) {
        try {
            return client.getSubscriptionManager().createSubscription(timeDeadband).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException("Could not create a subscription", e);
        }
    }

    public CompletableFuture<Void> deleteSubscription(UaSubscription subscription) {
        return CompletableFuture.allOf(client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
    }

    public boolean isConnected() {
        if (client == null) {
            return false;
        }
        try {
            client.getSession().get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<Void> deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) {
        List<UaMonitoredItem> itemsToRemove = new ArrayList<>();
        for (UaMonitoredItem uaMonitoredItem : subscription.getMonitoredItems()) {
            if (clientHandle.equals(uaMonitoredItem.getClientHandle())) {
                itemsToRemove.add(uaMonitoredItem);
            }
        }
        return CompletableFuture.allOf(subscription.deleteMonitoredItems(itemsToRemove));
    }

    public CompletableFuture<List<UaMonitoredItem>> subscribeItemDefinitions (UaSubscription subscription,
                                                                              List<ItemDefinition> definitions,
                                                                              Deadband deadband,
                                                                              BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        List<MonitoredItemCreateRequest> requests = new ArrayList<>();
        for(ItemDefinition definition : definitions) {
            requests.add(createItemSubscriptionRequest(definition, deadband));
        }
        return subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback);
    }

    public CompletableFuture<List<DataValue>> read(NodeId nodeIds) {
        return client.readValues(0, TimestampsToReturn.Both, Collections.singletonList(nodeIds));
    }

    public CompletableFuture<StatusCode> write (NodeId nodeId, DataValue value) {
        return client.writeValue(nodeId, value);
    }

    private MonitoredItemCreateRequest createItemSubscriptionRequest(ItemDefinition definition, Deadband deadband) {
        // What is a sensible value for the queue size? Must be large enough to hold all notifications queued in between publishing cycles.
        // Currently, we are only keeping the newest value.
        int queueSize = 0;

        MonitoringParameters mp = new MonitoringParameters(definition.getClientHandle(), (double) deadband.getTime(),
                getFilter(deadband), uint(queueSize), true);

        ReadValueId id = new ReadValueId(definition.getAddress(), AttributeId.Value.uid(),null, QualifiedName.NULL_VALUE);
        return new MonitoredItemCreateRequest(id, MonitoringMode.Reporting, mp);
    }

    private ExtensionObject getFilter(Deadband deadband) {
        DataChangeFilter filter = new DataChangeFilter(DataChangeTrigger.StatusValue, uint(deadband.getType()), (double) deadband.getValue());
        return ExtensionObject.encode(client.getSerializationContext(), filter);
    }

    private static CompletableFuture<OpcUaClient> createClient (String uri, SecurityPolicy sp) throws OPCCommunicationException, ExecutionException, InterruptedException {
        return DiscoveryClient
                .getEndpoints(uri)
                .thenCompose(endpoints -> {
                    try {
                        return CompletableFuture.completedFuture(OpcUaClient.create(buildConfiguration(endpoints, sp)));
                    } catch (UaException e) {
                        CompletableFuture<OpcUaClient> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(new OPCCommunicationException("Could not create the client", e));
                        return failedFuture;
                    }
                });
    }

    private static OpcUaClientConfig buildConfiguration(final List<EndpointDescription> endpoints, SecurityPolicy sp) {
        EndpointDescription endpoint = filterEndpoints(endpoints, sp);
        return OpcUaClientConfig.builder().setEndpoint(endpoint).build();
    }

    private static EndpointDescription filterEndpoints (List<EndpointDescription> endpoints, SecurityPolicy sp) throws OPCCommunicationException {
        return endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(sp.getUri()))
                .findFirst()
                .orElseThrow(() -> new OPCCommunicationException("The server does not offer any endpoints matching the configuration."));
    }

}
