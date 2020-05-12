package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.*;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cern.c2mon.daq.opcua.exceptions.CommunicationException.rethrow;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * The implementation of custom wrapper for the OPC UA milo client
 */
@Slf4j
@RequiredArgsConstructor
@Component(value = "miloEndpoint")
@Primary
public class MiloEndpoint implements Endpoint {

    private final SecurityModule securityModule;

    private OpcUaClient client;

    /**
     * Connects to a server through OPC. Discover available endpoints and select the most secure one in line with
     * configuration options. If the server with the same URI is already connected, the connection is kept as-is.
     *
     * @param uri the server address to connect to
     * @throws CommunicationException if an execution exception occurs either during endpoint discovery, or when
     *                                creating the client.
     */
    @Override
    public void initialize(String uri) throws CommunicationException, ConfigurationException {
        if (!isConnectedTo(uri)) {
            try {
                var endpoints = DiscoveryClient.getEndpoints(uri).get();
                endpoints = updateEndpointUrls(uri, endpoints);
                client = securityModule.createClient(endpoints);
            } catch (ExecutionException | InterruptedException e) {
                rethrow(ExceptionContext.CONNECT, e);
            }
        }
    }

    /**
     * Disconnect from the server
     *
     * @throws CommunicationException if an execution exception occurs on disconnecting from the client.
     */
    @Override
    public void disconnect() throws CommunicationException {
        if (isConnected()) {
            try {
                client = client.disconnect().get();
            } catch (ExecutionException | InterruptedException e) {
                rethrow(ExceptionContext.DISCONNECT, e);
            }
        }
    }

    /**
     * Check whether the client is currently connected to a server.
     *
     * @return true if the session is active and the client is therefore connected to a server
     */
    @Override
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

    /**
     * Create and return a new subscription.
     *
     * @param timeDeadband The subscription's publishing interval in milliseconds. If 0, the Server will use the fastest
     *                     supported interval.
     * @return the newly created subscription
     * @throws CommunicationException if an execution exception occurs on creating a subscription
     */
    public UaSubscription createSubscription(int timeDeadband) throws CommunicationException {
        try {
            return client.getSubscriptionManager().createSubscription(timeDeadband).get();
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.CREATE_SUBSCRIPTION, e);
            return null;
        }
    }

    /**
     * Delete an existing subscription
     *
     * @param subscription The subscription to delete
     * @throws CommunicationException if an execution exception occurs on deleting a subscription
     */
    @Override
    public void deleteSubscription(UaSubscription subscription) throws CommunicationException {
        try {
            client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()).get();
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.DELETE_SUBSCRIPTION, e);
        }
    }

    /**
     * Add a list of item definitions as monitored items to a subscription
     *
     * @param subscription         The subscription to add the monitored items to
     * @param definitions          the {@link cern.c2mon.daq.opcua.mapping.ItemDefinition}s for which to create
     *                             monitored items
     * @param itemCreationCallback the callback function to execute when each item has been created successfully
     * @return a list of monitored items corresponding to the item definitions
     * @throws CommunicationException if an execution exception occurs on creating the monitored items
     */
    @Override
    public List<UaMonitoredItem> subscribeItemDefinitions(UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback)
            throws CommunicationException {
        List<MonitoredItemCreateRequest> requests = definitions.stream()
                .map(this::createItemSubscriptionRequest)
                .collect(Collectors.toList());
        try {
            return subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback).get();
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.CREATE_MONITORED_ITEM, e);
            return null;
        }
    }

    /**
     * Delete a monitored item from an OPC UA subscription
     *
     * @param clientHandle the identifier of the monitored item to remove
     * @param subscription the subscription to remove the monitored item from.
     * @throws CommunicationException if an execution exception occurs on deleting an item from a subscription
     */
    @Override
    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws CommunicationException {
        List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems().stream()
                .filter(i -> clientHandle.equals(i.getClientHandle()))
                .collect(Collectors.toList());
        try {
            subscription.deleteMonitoredItems(itemsToRemove).get();
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.DELETE_MONITORED_ITEM, e);
        }
    }

    /**
     * Read the current value from a node on the currently connected OPC UA server
     *
     * @param nodeId the nodeId of the node whose value to read
     * @return the {@link DataValue} returned by the OPC UA stack upon reading the node
     * @throws CommunicationException if an execution exception occurs on reading from the node
     */
    @Override
    public DataValue read(NodeId nodeId) throws CommunicationException {
        try {
            return client.readValue(0, TimestampsToReturn.Both, nodeId).get();
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.READ, e);
            return null;
        }
    }

    /**
     * Write a value to a node on the currently connected OPC UA server
     *
     * @param nodeId the nodeId of the node to write to
     * @param value  the value to write to the node
     * @return the {@link StatusCode} of the response to the write action
     * @throws CommunicationException if an execution exception occurs on writing to the node
     */
    @Override
    public StatusCode write(NodeId nodeId, Object value) throws CommunicationException {
        // Many OPC UA Servers are unable to deal with StatusCode or DateTime, hence set to null
        DataValue dataValue = new DataValue(new Variant(value), null, null);
        try {
            return client.writeValue(nodeId, dataValue).get();
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.WRITE, e);
            return null;
        }
    }

    /**
     * Call the method Node with ID methodId contained in the object with ID objectId.
     *
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args     the input arguments to pass to the methodId call.
     * @return A Map.Entry containing the StatusCode of the methodId response as key, and the methodId's output
     * arguments (if applicable)
     * @throws CommunicationException if an execution exception occurs on calling the method node
     */
    public Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args) throws CommunicationException {
        final Variant[] variants = Stream.of(args).map(Variant::new).toArray(Variant[]::new);
        final CallMethodRequest request = new CallMethodRequest(objectId, methodId, variants);
        try {
            final CallMethodResult methodResult = client.call(request).get();
            Object[] output = MiloMapper.toObject(methodResult.getOutputArguments());
            return Map.entry(methodResult.getStatusCode(), output);
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.METHOD, e);
            return null;
        }
    }

    /**
     * Fetches the node's first parent object node, if such a node exists.
     *
     * @param nodeId the node whose parent to fetch
     * @return the parent node's NodeId
     * @throws CommunicationException if an execution exception occurs during browsing the server namespace
     * @throws ConfigurationException if the browse was successful, but no parent object node could be found
     */
    public NodeId getParentObjectNodeId(NodeId nodeId) throws ConfigurationException, CommunicationException {
        final BrowseDescription bd = new BrowseDescription(
                nodeId,
                BrowseDirection.Inverse,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue()),
                uint(BrowseResultMask.All.getValue()));
        try {
            final BrowseResult result = client.browse(bd).get();
            if (result.getReferences() != null && result.getReferences().length > 0) {
                final Optional<NodeId> objectNode = result.getReferences()[0].getNodeId().local(client.getNamespaceTable());
                if (result.getStatusCode().isGood() && objectNode.isPresent()) {
                    return objectNode.get();
                }
            }
            throw new ConfigurationException(ConfigurationException.Cause.OBJINVALID, "No object node enclosing method node with nodeID " + nodeId + " could be found.");
        } catch (ExecutionException | InterruptedException e) {
            rethrow(ExceptionContext.BROWSE, e);
            return null;
        }
    }

    private MonitoredItemCreateRequest createItemSubscriptionRequest(DataTagDefinition definition) {
        // If a static time deadband is configured, only one value per publishing cycle will be kept by the DAQ core.
        // Therefore queueSize can be 0 (only the newest value is held in between publishing cycles)
        UInteger queueSize = uint(0);

        // If the samplingInterval is set to 0, the source will provide updates at the fastest possible rate.
        double samplingInterval = 0;

        DataChangeFilter filter = new DataChangeFilter(DataChangeTrigger.StatusValue,
                uint(definition.getValueDeadbandType()),
                (double) definition.getValueDeadband());
        MonitoringParameters mp = new MonitoringParameters(definition.getClientHandle(),
                samplingInterval,
                ExtensionObject.encode(client.getSerializationContext(), filter),
                queueSize,
                true);
        ReadValueId id = new ReadValueId(definition.getNodeId(),
                AttributeId.Value.uid(),
                null,
                QualifiedName.NULL_VALUE);
        return new MonitoredItemCreateRequest(id, MonitoringMode.Reporting, mp);
    }

    /**
     * There is a common misconfiguration in OPC UA servers to return a local hostname in the endpointUrl that can not
     * be resolved by the client. Replace the hostname of each endpoint's URL by the one used to originally reach the
     * server to work around the issue.
     *
     * @param originalUri       the (resolvable) uri used to originally reach the server.
     * @param originalEndpoints a list of originalEndpoints returned by the DiscoveryClient potentially containing
     *                          incorrect endpointUrls
     * @return a list of endpoints with the original url as as endpointUrl, but other identical to the
     * originalEndpoints.
     */
    private static List<EndpointDescription> updateEndpointUrls(String originalUri, List<EndpointDescription> originalEndpoints) {
        // Some hostnames contain characters not allowed in a URI, such as underscores in Windows machine hostnames.
        // Therefore, parsing is done using a regular expression rather than relying on java URI class methods.
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

    private boolean isConnectedTo(String currentUri) {
        if (isConnected()) {
            final var previousUri = client.getConfig().getEndpoint().getEndpointUrl();
            return previousUri.equalsIgnoreCase(currentUri);
        }
        return false;
    }
}
