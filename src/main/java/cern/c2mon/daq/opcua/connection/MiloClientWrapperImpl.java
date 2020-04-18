package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.security.SecurityModule;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.*;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.eclipse.milo.opcua.stack.core.util.ConversionUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Cause.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * The implementation of custom wrapper for the OPC UA milo client
 */
@Slf4j
@NoArgsConstructor
public class MiloClientWrapperImpl implements MiloClientWrapper {

    @Autowired
    @Setter
    AppConfig config;

    private OpcUaClient client;

    private String uri;

    @Autowired
    @Setter
    private SecurityModule securityModule;

    /**
     * Creates a new instance of a wrapper for the Eclipse milo OPC UA client
     * @param uri the endpoint uri that the client will connect to
     */
    public void initialize(String uri) {
        this.uri = uri;
    }

    /**
     * Unless otherwise configured, the client attempts to connect to the most secure endpoint first, where Milo's
     * endpoint security level is taken as a measure. The preferred means of authentication is using a predefined
     * certificate, the details of which are given in AppConfig. If this is not configured, the client generates a
     * self-signed certificate.
     * Connection without security is only taken as a last resort if the above strategies fail, and can be disabled
     * completely in the configuration.
git p     */
    public void initialize () {
        try {
            List<EndpointDescription> endpointDescriptions = DiscoveryClient.getEndpoints(uri).get();
            client = securityModule.createClient(endpointDescriptions);
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(CONNECT, e);
        }
    }

    public void addEndpointSubscriptionListener(EndpointSubscriptionListener listener) {
        client.getSubscriptionManager().addSubscriptionListener(listener);
    }

    public void disconnect() {
        //TODO: find a more elegant way to do this that works with the test MessageHandlerTest framework
        if (client != null) {
            try {
                client = client.disconnect().get();
                Stack.releaseSharedResources();
            } catch (InterruptedException | ExecutionException e) {
                throw asOPCCommunicationException(DISCONNECT, e);
            }
        }
    }

    /**
     *
     * @param timeDeadband The subscription's publishing interval in milliseconds.
     *                     If 0, the Server will use the fastest supported interval
     * @return the newly created subscription
     */
    public UaSubscription createSubscription(int timeDeadband) {
        try {
            return client.getSubscriptionManager().createSubscription(timeDeadband).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(CREATE_SUBSCRIPTION, e);
        }
    }

    public void deleteSubscription(UaSubscription subscription) {
        try {
            client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(DELETE_SUBSCRIPTION, e);
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

    @Override
    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) {
        List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems().stream()
                .filter(i -> clientHandle.equals(i.getClientHandle()))
                .collect(Collectors.toList());
        try {
            subscription.deleteMonitoredItems(itemsToRemove).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(DELETE_MONITORED_ITEM, e);
        }
    }

    public List<UaMonitoredItem> subscribeItemDefinitions (UaSubscription subscription,
                                                                              List<DataTagDefinition> definitions,
                                                                              Deadband deadband,
                                                                              BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) {
        List<MonitoredItemCreateRequest> requests =definitions.stream()
                .map(d -> createItemSubscriptionRequest(d, deadband))
                .collect(Collectors.toList());
        try {
            return subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(SUBSCRIBE_TAGS, e);
        }
    }

    public List<DataValue> read(NodeId nodeIds) {
        try {
            return client.readValues(0, TimestampsToReturn.Both, Collections.singletonList(nodeIds)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(READ, e);
        }
    }

    public StatusCode write (NodeId nodeId, Object value) {
        DataValue dataValue = new DataValue(new Variant(value), null, null);
        try {
            // Many OPC UA Servers are unable to deal with StatusCode or DateTime, hence set to null
            return client.writeValue(nodeId, dataValue).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(WRITE, e);
        }
    }

    private MonitoredItemCreateRequest createItemSubscriptionRequest(DataTagDefinition definition, Deadband deadband) {
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
    /**
     * Not yet in use - use for subscribing to a group of nodes if deemed useful.
     * @param indent applied as often to a node as the hierarchy is deep
     * @param browseRoot The Node to start browsing from
     */
    public void browseNode(String indent, NodeId browseRoot) {
        BrowseDescription browse = new BrowseDescription(
                browseRoot,
                BrowseDirection.Forward,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                uint(BrowseResultMask.All.getValue())
        );

        try {
            BrowseResult browseResult = client.browse(browse).get();

            List<ReferenceDescription> references = ConversionUtil.toList(browseResult.getReferences());

            for (ReferenceDescription rd : references) {
                log.info("{} Node={}, NodeId={}", indent, rd.getBrowseName().getName(), rd.getNodeId().toString());

                // recursively browse to children
                rd.getNodeId().local().ifPresent(nodeId -> browseNode(indent + "  ", nodeId));
            }
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(OPCCommunicationException.Cause.BROWSE, e);
        }
    }

    private OPCCommunicationException asOPCCommunicationException(OPCCommunicationException.Cause cause, Exception e) {
        Throwable t = e;
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            t = e.getCause();
        } else if (e instanceof ExecutionException) {
            t = e.getCause();
        }
        return new OPCCommunicationException(cause, t);
    }
}
