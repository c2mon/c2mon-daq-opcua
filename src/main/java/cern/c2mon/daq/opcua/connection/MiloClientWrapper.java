package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.Deadband;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.type.TypeConverter;
import lombok.RequiredArgsConstructor;
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
import org.eclipse.milo.opcua.stack.core.util.TypeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Cause.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * The implementation of custom wrapper for the OPC UA milo client
 */
@Slf4j
@RequiredArgsConstructor
@Component("wrapper")
public class MiloClientWrapper implements ClientWrapper {

    @Autowired
    private final SecurityModule securityModule;

    private OpcUaClient client;

    /**
     * Unless otherwise configured, the client attempts to connect to the most secure endpoint first, where Milo's
     * endpoint security level is taken as a measure. The preferred means of authentication is using a predefined
     * certificate, the details of which are given in AppConfig. If this is not configured, the client generates a
     * self-signed certificate.
     * Connection without security is only taken as a last resort if the above strategies fail, and can be disabled
     * completely in the configuration. */
    public void initialize (String uri) {
        try {
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(uri).get();
            endpoints = updateEndpointUrls(uri, endpoints);
            client = securityModule.createClient(endpoints);
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
        List<MonitoredItemCreateRequest> requests = definitions.stream()
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

    public Map.Entry<StatusCode, Object[]> callMethod(ItemDefinition definition, Object... args) {
        return definition.getMethodNodeId() == null ?
                callMethod(getParentObjectNodeId(definition.getNodeId()), definition.getNodeId(), args) :
                callMethod(definition.getNodeId(), definition.getMethodNodeId(), args);
    }

    public Map.Entry<StatusCode, Object[]> callMethod(NodeId object, NodeId method, Object... args) {
        final Variant[] variants = Stream.of(args).map(Variant::new).toArray(Variant[]::new);
        try {
            final CallMethodRequest request = new CallMethodRequest(object, method, variants);
            final CallMethodResult methodResult = client.call(request).get();
            Object[] output = Stream.of(methodResult.getOutputArguments())
                    .map(this::toObject)
                    .filter(Objects::nonNull)
                    .toArray();
            return Map.entry(methodResult.getStatusCode(), output);
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(METHOD, e);
        }
    }

    private Object toObject(Variant variant) {
        final Optional<NodeId> dataType = variant.getDataType();
        if (dataType.isPresent()) {
            final Class<?> objectClass = TypeUtil.getBackingClass(dataType.get());
            if (objectClass != null) {
                final String className = objectClass.getName();
                if (TypeConverter.isConvertible(variant.getValue(), className)) {
                    return TypeConverter.cast(variant.getValue(), className);
                }
            }
        }
        return null;
    }

    private NodeId getParentObjectNodeId(NodeId nodeId) {
        final BrowseDescription bd = new BrowseDescription(
                nodeId,
                BrowseDirection.Inverse,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue()),
                uint(BrowseResultMask.All.getValue()));
        final BrowseResult result;
        try {
            result = client.browse(bd).get();
        } catch (InterruptedException | ExecutionException e) {
            throw asOPCCommunicationException(GETOBJ, e);
        }
        if (result.getReferences() != null && result.getReferences().length > 0) {
            final Optional<NodeId> objectNode = result.getReferences()[0].getNodeId().local(null);
            if (result.getStatusCode().isGood() && objectNode.isPresent()) {
                return objectNode.get();
            }
        }
        throw new OPCCommunicationException(OBJINVALID);
    }



    private MonitoredItemCreateRequest createItemSubscriptionRequest(DataTagDefinition definition, Deadband deadband) {
        // TODO: What is a sensible value for the queue size? Must be large enough to hold all notifications queued in between publishing cycles.
        // Currently, we are only keeping the newest value.
        int queueSize = 0;
        DataChangeFilter filter = new DataChangeFilter(DataChangeTrigger.StatusValue, uint(deadband.getType()), (double) deadband.getValue());
        ExtensionObject encodedFilter =  ExtensionObject.encode(client.getSerializationContext(), filter);
        MonitoringParameters mp = new MonitoringParameters(
                definition.getClientHandle(),
                (double) deadband.getTime(),
                encodedFilter,
                uint(queueSize),
                true);
        ReadValueId id = new ReadValueId(definition.getNodeId(),
                AttributeId.Value.uid(),
                null,
                QualifiedName.NULL_VALUE);
        return new MonitoredItemCreateRequest(id, MonitoringMode.Reporting, mp);
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

    /**
     * There is a common misconfiguration in OPC UA servers to return a local hostname in the endpointUrl that can not
     * be resolved by the client. Replace the hostname of each endpoint's URL by the one used to originally reach the
     * server to work around the issue.
     * @param originalUri the (resolvable) uri used to originally reach the server.
     * @param originalEndpoints a list of originalEndpoints returned by the DiscoveryClient potentially containing incorrect endpointUrls
     * @return a list of endpoints with the original url as as endpointUrl, but other identical to the originalEndpoints.
     */
    private static List<EndpointDescription> updateEndpointUrls(String originalUri, List<EndpointDescription> originalEndpoints) {
        // Some hostnames contain characters not allowed in a URI (such as underscores in Windows machine hostnames),
        // hence parsing manually rather than relying on URI class methods.
        final String hostRegex = "://[^/]*";
        final Matcher matcher = Pattern.compile(hostRegex).matcher(originalUri);
        if (matcher.find()) {
            return originalEndpoints.stream().map(e -> new EndpointDescription(
                    e.getEndpointUrl().replaceAll(hostRegex, matcher.group()),
                    e.getServer(),
                    e.getServerCertificate(),
                    e.getSecurityMode(),
                    e.getSecurityPolicyUri(),
                    e.getUserIdentityTokens(),
                    e.getTransportProfileUri(),
                    e.getSecurityLevel()))
                    .collect(Collectors.toList());
        }
        return originalEndpoints;
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
