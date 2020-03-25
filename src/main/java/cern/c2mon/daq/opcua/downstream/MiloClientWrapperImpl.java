package cern.c2mon.daq.opcua.downstream;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Cause.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * The implementation of custom wrapper for the OPC UA milo client
 */
@Slf4j
public class MiloClientWrapperImpl implements MiloClientWrapper {

    private OpcUaClient client;

    private final String uri;
    private final Certifier cert;

    /**
     * Creates a new instance of a wrapper for the Eclipse milo OPC UA client
     * @param uri the endpoint uri that the client will connect to
     * @param cert the certifier policy that the client and endpoint will adhere to
     */
    public MiloClientWrapperImpl(String uri, Certifier cert) {
        this.uri = uri;
        this.cert = cert;
    }

    public void initialize () {
        try {
            client = createClient();
        } catch (Exception e) {
            throw new OPCCommunicationException(CREATE_CLIENT, e);
        }
    }

    public void connect() throws OPCCommunicationException {
        try {
            client = (OpcUaClient) client.connect().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(CONNECT, e);
        }
    }

    public void addEndpointSubscriptionListener(EndpointSubscriptionListener listener) {
        client.getSubscriptionManager().addSubscriptionListener(listener);
    }

    public void disconnect() throws OPCCommunicationException {
        //TODO: find a more elegant way to do this that works with the test MessageHandlerTest framework
        if (client != null) {
            try {
                client = client.disconnect().get();
                Stack.releaseSharedResources();
            } catch (InterruptedException | ExecutionException e) {
                throw new OPCCommunicationException(DISCONNECT, e);
            }
        }
    }

    /**
     *
     * @param timeDeadband The subscription's publishing interval in milliseconds.
     *                     If 0, the Server will use the fastest supported interval
     * @return the newly created subscription
     */
    public UaSubscription createSubscription(int timeDeadband) throws OPCCommunicationException {
        try {
            return client.getSubscriptionManager().createSubscription(timeDeadband).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(CREATE_SUBSCRIPTION, e);
        }
    }

    public void deleteSubscription(UaSubscription subscription) throws OPCCommunicationException {
        try {
            client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(DELETE_SUBSCRIPTION, e);
        }
    }

    public boolean isConnected() {
        if (client == null) {
            return false;
        }
        try {
            client.getSession().get();
            return true;
        } catch (Exception e) {
            log.info("Client not connected.");
            return false;
        }
    }

    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws OPCCommunicationException {
        List<UaMonitoredItem> itemsToRemove = new ArrayList<>();
        for (UaMonitoredItem uaMonitoredItem : subscription.getMonitoredItems()) {
            if (clientHandle.equals(uaMonitoredItem.getClientHandle())) {
                itemsToRemove.add(uaMonitoredItem);
            }
        }
        try {
            subscription.deleteMonitoredItems(itemsToRemove).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(DELETE_MONITORED_ITEM, e);
        }
    }

    public List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription,
                                                                              List<ItemDefinition> definitions,
                                                                              Deadband deadband,
                                                                              BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCCommunicationException {
        List<MonitoredItemCreateRequest> requests = new ArrayList<>();
        for(ItemDefinition definition : definitions) {
            requests.add(createItemSubscriptionRequest(definition, deadband));
        }
        try {
            return subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(SUBSCRIBE_TAGS, e);
        }
    }

    public List<DataValue> read(NodeId nodeIds) throws OPCCommunicationException {
        try {
            return client.readValues(0, TimestampsToReturn.Both, Collections.singletonList(nodeIds)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(READ, e);
        }
    }

    public StatusCode write (NodeId nodeId, DataValue value) throws OPCCommunicationException {
        try {
            return client.writeValue(nodeId, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new OPCCommunicationException(WRITE, e);
        }
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

    private OpcUaClient createClient() throws Exception {
        List<EndpointDescription> endpointDescriptions = DiscoveryClient.getEndpoints(uri).get();
        return OpcUaClient.create(buildConfiguration(endpointDescriptions));
    }

    private OpcUaClientConfig buildConfiguration(final List<EndpointDescription> endpoints) throws Exception {
        EndpointDescription endpoint = chooseEndpoint(endpoints, cert.getSecurityPolicy(), cert.getMessageSecurityMode());
        OpcUaClientConfigBuilder builder = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("C2MON OPC UA DAQ Process"))
                .setRequestTimeout(uint(5000))
                .setEndpoint(endpoint);
         return cert.configureSecuritySettings(builder).build();
    }
    private static EndpointDescription chooseEndpoint (List<EndpointDescription> endpoints, SecurityPolicy sp, MessageSecurityMode minMessageSecurityMode) {
        return endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(sp.getUri()))
                .findFirst()
                .orElseThrow(() -> new OPCCommunicationException(ENDPOINTS));
    }
}
