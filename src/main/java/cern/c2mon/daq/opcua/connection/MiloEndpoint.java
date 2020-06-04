package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.DataTagDefinition;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

import static cern.c2mon.daq.opcua.exceptions.CommunicationException.of;
import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * The implementation of wrapper for the OPC UA milo client. Except for the initialize and getParentObjectNodeId
 * methods, all thrown {@link OPCUAException}s can be either of type {@link CommunicationException} or {@link
 * LongLostConnectionException}. A CommunicationException ocurrs when the method has been attempted as often and with a
 * delay as configured without success. A LongLostConnectionException is thrown when the connection has already been
 * lost for the time needed to execute all configured attempts. In this case, no retries are executed.
 */
@Slf4j
@RequiredArgsConstructor
@Component(value = "miloEndpoint")
@Primary
public class MiloEndpoint implements Endpoint {

    private final SecurityModule securityModule;
    private final SessionActivityListener endpointListener;
    private final EndpointSubscriptionListener subscriptionListener;
    private final RetryDelegate retryDelegate;
    private OpcUaClient client;

    /**
     * There is a common misconfiguration in OPC UA servers to return a local hostname in the endpointUrl that can not
     * be resolved by the client. Replace the hostname of each endpoint's URL by the one used to originally reach the
     * server to work around the issue.
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

    /**
     * Connects to a server through the Milo OPC UA SDK. Discover available endpoints and select the most secure one in
     * line with configuration options. In case of a communication error this method is retried a given amount of times
     * with a given delay as specified in the application configuration.
     * @param uri the server address to connect to.
     * @throws OPCUAException       of type {@link CommunicationException} if it is not possible to connect to the OPC
     *                              UA server within the configured number of attempts, and of type {@link
     *                              ConfigurationException} if it is not possible to connect to any of the the OPC UA
     *                              server's endpoints with the given authentication configuration settings.
     * @throws InterruptedException if the method was interrupted during execution.
     */
    @Override
    @Retryable(value = {CommunicationException.class},
            maxAttemptsExpression = "${app.maxRetryAttempts}",
            backoff = @Backoff(delayExpression = "${app.retryDelay}"))
    public void initialize(String uri) throws OPCUAException, InterruptedException {
        try {
            var endpoints = DiscoveryClient.getEndpoints(uri).get();
            endpoints = updateEndpointUrls(uri, endpoints);
            client = securityModule.createClientWithListener(endpoints, endpointListener, retryDelegate);
            client.getSubscriptionManager().addSubscriptionListener(subscriptionListener);
            log.info("Connected");
        } catch (ExecutionException e) {
            throw of(CONNECT, e.getCause(), false);
        }
    }

    /**
     * Disconnect from the server with a certain number of retries in case of connection error.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public void disconnect() throws OPCUAException {
        if (client != null) {
            client.removeSessionActivityListener(endpointListener);
            client.removeSessionActivityListener(retryDelegate);
            client.getSubscriptionManager().clearSubscriptions();
            retryDelegate.completeOrThrow(DISCONNECT, client::disconnect);
        }
    }

    /**
     * Create and return a new subscription with a certain number of retries in case of connection error.
     * @param timeDeadband The subscription's publishing interval in milliseconds. If 0, the Server will use the fastest
     *                     supported interval.
     * @return the newly created subscription.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    public UaSubscription createSubscription(int timeDeadband) throws OPCUAException {
        return retryDelegate.completeOrThrow(CREATE_SUBSCRIPTION, () -> client.getSubscriptionManager().createSubscription(timeDeadband));
    }

    /**
     * Delete an existing subscription with a certain number of retries in case of connection error.
     * @param subscription The subscription to delete along with all contained MonitoredItems.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public void deleteSubscription(UaSubscription subscription) throws OPCUAException {
        retryDelegate.completeOrThrow(DELETE_SUBSCRIPTION, () -> client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId()));
    }

    /**
     * Add a list of item definitions as monitored items to a subscription with a certain number of retries in case of
     * connection error.
     * @param subscription         The subscription to add the monitored items to
     * @param definitions          the {@link ItemDefinition}s for which to create monitored items
     * @param itemCreationCallback the callback function to execute when each item has been created successfully
     * @return a list of monitored items corresponding to the item definitions
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public List<UaMonitoredItem> subscribeItem(UaSubscription subscription, List<DataTagDefinition> definitions, BiConsumer<UaMonitoredItem, Integer> itemCreationCallback) throws OPCUAException {
        List<MonitoredItemCreateRequest> requests = definitions.stream()
                .map(this::createItemSubscriptionRequest)
                .collect(Collectors.toList());
        return retryDelegate.completeOrThrow(CREATE_MONITORED_ITEM, () -> subscription.createMonitoredItems(TimestampsToReturn.Both, requests, itemCreationCallback));

    }

    /**
     * Delete a monitored item from an OPC UA subscription with a certain number of retries in case of connection
     * error.
     * @param clientHandle the identifier of the monitored item to remove.
     * @param subscription the subscription to remove the monitored item from.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public void deleteItemFromSubscription(UInteger clientHandle, UaSubscription subscription) throws OPCUAException {
        List<UaMonitoredItem> itemsToRemove = subscription.getMonitoredItems().stream()
                .filter(i -> clientHandle.equals(i.getClientHandle()))
                .collect(Collectors.toList());
        retryDelegate.completeOrThrow(DELETE_MONITORED_ITEM, () -> subscription.deleteMonitoredItems(itemsToRemove));
    }

    /**
     * Read the current value from a node on the currently connected OPC UA server with a certain number of retries in
     * case connection of error.
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link DataValue} containing the value read from the node.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public DataValue read(NodeId nodeId) throws OPCUAException {
        return retryDelegate.completeOrThrow(READ, () -> client.readValue(0, TimestampsToReturn.Both, nodeId));
    }

    /**
     * Write a value to a node on the currently connected OPC UA server with a certain number of retries in case of
     * connection error.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return a completable future  the {@link StatusCode} of the response to the write action
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public StatusCode write(NodeId nodeId, Object value) throws OPCUAException {
        // Many OPC UA Servers are unable to deal with StatusCode or DateTime, hence set to null
        DataValue dataValue = new DataValue(new Variant(value), null, null);
        return retryDelegate.completeOrThrow(WRITE, () -> client.writeValue(nodeId, dataValue));
    }

    /**
     * Call the method node with ID methodId contained in the object with ID objectId with a certain number of retries
     * in case of connection error.
     * @param objectId the nodeId of class Object containing the method node
     * @param methodId the nodeId of class Method which shall be called
     * @param args     the input arguments to pass to the methodId call.
     * @return the StatusCode of the methodId response as key and the output arguments of the called method (if
     * applicable, else null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    public Map.Entry<StatusCode, Object[]> callMethod(NodeId objectId, NodeId methodId, Object... args) throws OPCUAException {
        final var variants = Stream.of(args).map(Variant::new).toArray(Variant[]::new);
        final var result = retryDelegate.completeOrThrow(METHOD, () -> client.call(new CallMethodRequest(objectId, methodId, variants)));
        final var output = MiloMapper.toObject(result.getOutputArguments());
        return Map.entry(result.getStatusCode(), output);
    }

    /**
     * Fetches the node's first parent object node, if such a node exists with a certain number of retries in case of
     * connection error.
     * @param nodeId the node whose parent object to fetch
     * @return the parent node's NodeId, if it exists. Else a {@link ConfigurationException} is thrown.
     * @throws OPCUAException of type {@link ConfigurationException} in case the nodeId is orphaned, or of types {@link
     *                        CommunicationException} or {@link LongLostConnectionException} as detailed in the
     *                        implementation class's JavaDoc.
     */
    public NodeId getParentObjectNodeId(NodeId nodeId) throws OPCUAException {
        final BrowseDescription bd = new BrowseDescription(
                nodeId,
                BrowseDirection.Inverse,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue()),
                uint(BrowseResultMask.All.getValue()));
        final BrowseResult result = retryDelegate.completeOrThrow(BROWSE, () -> client.browse(bd));
        if (result.getReferences() != null && result.getReferences().length > 0) {
            final Optional<NodeId> objectNode = result.getReferences()[0].getNodeId().local(client.getNamespaceTable());
            if (result.getStatusCode().isGood() && objectNode.isPresent()) {
                return objectNode.get();
            }
        }
        throw new ConfigurationException(OBJINVALID);
    }

    /**
     * Check whether a subscription is subscribed as part of the current session.
     * @param subscription the subscription to check for currentness.
     * @return true if the subscription is a valid in the current session.
     */
    public boolean isCurrent(UaSubscription subscription) {
        return client.getSubscriptionManager().getSubscriptions().contains(subscription);
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
}
