package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.*;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * This class is responsible for fetching a server's redundancy support information and for delegating to the proper
 * {@link Controller} according to the application properties in {@link AppConfigProperties} and to server
 * capabilities.
 */
@Slf4j
@ManagedResource(objectName = "controller")
@RequiredArgsConstructor
@EquipmentScoped
public class ControllerProxy implements Controller {

    protected final ControllerFactory controllerFactory;
    protected final AppConfigProperties config;
    protected final Endpoint endpoint;
    protected ConcreteController controller;

    /**
     * Returns the simple name of the controller for the failover mode currently in use. To be invoked as an actuator
     * operation.
     * @return the simple name of the controller for the failover mode currently in use.
     */
    @ManagedOperation(description = "Find the controller name for the failover mode currently in use.")
    public String getFailoverMode () {
        return controller == null ? "Currently not connected." : controller.getClass().getSimpleName();
    }

    /**
     * Delegated failover handling to an appropriate {@link Controller} according to the application configuration or,
     * secondarily, according to server capabilities. If no redundant Uris are preconfigured, they are read from the
     * server's ServerUriArray node. In case of errors in the configuration and when reading from the server's address
     * space, the FailoverProxy treats the server as not part of a redundant setup. Notify the {@link
     * cern.c2mon.daq.common.IEquipmentMessageSender} if a failure occurs which prevents the DAQ from starting
     * successfully.
     * @param serverAddresses the uris of all servers in a redundant server set. If the server is not redundant, the
     *                        collection includes the uri of the server to connect to.
     * @throws OPCUAException of type {@link CommunicationException} if an error not related to authentication
     *                        configuration occurs on connecting to the OPC UA server, and of type {@link
     *                        ConfigurationException} if it is not possible to connect to any of the the OPC UA server's
     *                        endpoints with the given authentication configuration settings.
     */
    @Override
    public void connect (Collection<String> serverAddresses) throws OPCUAException {
        log.error("Attempting Connection to URIs {}.", StringUtils.join(serverAddresses, ","));
        String currentUri = establishInitialConnection(serverAddresses);
        log.info("Connection established to server at URI {}.", currentUri);
        String[] redundantUris = serverAddresses.stream()
                .filter(s -> !s.equalsIgnoreCase(currentUri))
                .toArray(String[]::new);
        if (config.getRedundancyMode() != null) {
            controller = controllerFactory.getObject(config.getRedundancyMode());
            if (controller instanceof FailoverController && redundantUris.length == 0) {
                redundantUris = loadRedundantUris(currentUri, null);
            }
        } else {
            redundantUris = loadControllerAndUrisFromAddressSpace(currentUri);
        }
        log.info("Using redundancy mode {}, redundant URIs {}.", controller.getClass().getName(), redundantUris);
        controller.initialize(endpoint, redundantUris);
    }

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    @Override
    public void stop () {
        if (controller != null) {
            controller.stop();
        }
    }

    /**
     * Subscribes the {@link NodeId}s in the {@link ItemDefinition}s to the corresponding {@link SubscriptionGroup} on
     * the OPC UA server or on all servers in a redundant server set.
     * @param groupsWithDefinitions a Map of {@link SubscriptionGroup}s and the {@link ItemDefinition}s to subscribe to
     *                              the groups.
     */
    @Override
    public Map<Integer, SourceDataTagQuality> subscribe (Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        return controller.subscribe(groupsWithDefinitions);
    }

    /**
     * Unsubscribe from the {@link NodeId} in the {@link ItemDefinition} on the OPC UA server or on all servers in a
     * redundant server set.
     * @param definition the {@link ItemDefinition} to unsubscribe from.
     * @return whether the removal from subscription was completed successfully at the currently active server. False if
     * the {@link ItemDefinition} was not subscribed in the first place.
     */
    @Override
    public boolean unsubscribe (ItemDefinition definition) {
        return controller.unsubscribe(definition);
    }

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode} of the reading
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read (NodeId nodeId) throws OPCUAException {
        return controller.read(nodeId);
    }

    /**
     * Call the method node associated with the definition. It no method node is specified within the definition, it is
     * assumed that the primary node is the method node, and the first object node ID encountered during browse is used
     * as object node.
     * @param definition the definition containing the nodeId of Method which shall be called
     * @param arg        the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<Boolean, Object[]> callMethod (ItemDefinition definition, Object arg) throws OPCUAException {
        return controller.callMethod(definition, arg);
    }

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public boolean write (NodeId nodeId, Object value) throws OPCUAException {
        return controller.write(nodeId, value);
    }

    private String establishInitialConnection (Collection<String> serverAddresses) throws OPCUAException {
        String currentUri;
        for (Iterator<String> iterator = serverAddresses.iterator(); iterator.hasNext(); ) {
            currentUri = iterator.next();
            try {
                endpoint.initialize(currentUri);
                return currentUri;
            } catch (OPCUAException e) {
                if (!iterator.hasNext()) {
                    log.error("Could not connect to redundant URI {}, and no other server URIs remain.", currentUri);
                    throw e;
                }
                log.debug("Connection failed with exception:", e);
                log.info("Could not connect to redundant URI {}. Attempt next server...", currentUri);
            }
        }
        throw new ConfigurationException(ExceptionContext.NO_REDUNDANT_SERVER);
    }

    private String[] loadControllerAndUrisFromAddressSpace (String currentUri, String... redundantUris) throws OPCUAException {
        RedundancySupport redundancyMode = RedundancySupport.None;
        String[] redundantUriArray = new String[]{};
        try {
            final ServerRedundancyTypeNode redundancyNode = endpoint.getServerRedundancyNode();
            redundancyMode = redundancyNode.getRedundancySupport()
                    .thenApply(m -> (m == null) ? RedundancySupport.None : m)
                    .join();
            if (redundancyMode != RedundancySupport.None && redundancyMode != RedundancySupport.Transparent && redundantUris.length == 0) {
                redundantUriArray = loadRedundantUris(currentUri, redundancyNode);
            }
        } catch (CompletionException e) {
            log.error("An exception occurred when reading the server's redundancy information. Proceeding without setting up redundancy.", e);
        } finally {
            controller = controllerFactory.getObject(redundancyMode);
        }
        return redundantUriArray;
    }

    private String[] loadRedundantUris (String currentUri, ServerRedundancyTypeNode redundancyNode) throws OPCUAException {
        String[] redundantUriArray = loadRedundantUrisFromConfig(currentUri);
        if (redundantUriArray.length > 0) {
            return redundantUriArray;
        } else if (redundancyNode == null) {
            redundancyNode = endpoint.getServerRedundancyNode();
        }
        return loadRedundantUrisFromAddressSpace(currentUri, redundancyNode);
    }

    private String[] loadRedundantUrisFromConfig (String currentUri) {
        final Collection<String> redundantServerUris = config.getRedundantServerUris();
        if (redundantServerUris != null && !redundantServerUris.isEmpty()) {
            final String[] redundantUriArray = filterCurrentUri(currentUri, redundantServerUris.stream());
            log.info("Loaded redundant uris from configuration: {}.", redundantServerUris);
            return redundantUriArray;
        }
        return new String[]{};
    }

    private String[] loadRedundantUrisFromAddressSpace (String currentUri, ServerRedundancyTypeNode redundancyNode) {
        if (!NonTransparentRedundancyTypeNode.class.isAssignableFrom(redundancyNode.getClass())) {
            log.info("The redundancy mode does not require redundant addresses.");
            return new String[]{};
        }
        String[] redundantUriArray = ((NonTransparentRedundancyTypeNode) redundancyNode).getServerUriArray().join();
        redundantUriArray = filterCurrentUri(currentUri, Arrays.stream(redundantUriArray));
        log.info("Loaded redundant uri array from server: {}.", Arrays.toString(redundantUriArray));
        return redundantUriArray;
    }

    private String[] filterCurrentUri (String currentUri, Stream<String> redundantUriStream) {
        log.info("Filtering currently connected URI: {}.", currentUri);
        return redundantUriStream.filter(s -> !s.equalsIgnoreCase(currentUri)).toArray(String[]::new);
    }
}