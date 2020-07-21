package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * This class is responsible for fetching a server's redundancy support information and for delegating to the proper
 * {@link IControllerProxy} according to the application properties in {@link AppConfigProperties} and to server
 * capabilities.
 */
@Slf4j
@RequiredArgsConstructor
@Component(value = "failoverProxy")
@Primary
public class ControllerProxy implements IControllerProxy {
    protected final ApplicationContext appContext;
    protected final AppConfigProperties config;
    protected final Endpoint endpoint;
    protected Controller controller;

    /**
     * Delegated failover handling to an appropriate {@link IControllerProxy} according to the application configuration
     * or, secondarily, according to server capabilities. If no redundant Uris are preconfigured, they are read from the
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
    public void connect(Collection<String> serverAddresses) throws OPCUAException {
        final PeekingIterator<String> iterator = Iterators.peekingIterator(serverAddresses.iterator());
        final ArrayList<String> modifiableUris = new ArrayList<>(serverAddresses);
        String currentUri = iterator.peek();
        while (iterator.hasNext()) {
            currentUri = iterator.next();
            try {
                endpoint.initialize(currentUri);
                modifiableUris.remove(currentUri);
                break;
            } catch (CommunicationException e) {
                if (!iterator.hasNext()) {
                    log.error("Could not connect to redundant URI {}, and noother server URIs remain.", currentUri);
                    throw e;
                }
                log.info("Could not connect to redundant URI {}. Attempt next...", currentUri, e);
            }
        }
        String[] redundantUris = modifiableUris.toArray(String[]::new);

        final boolean wasFailoverLoadedFromConfig = loadedFailoverFromConfigurationSuccessfully();
        if (wasFailoverLoadedFromConfig && controller instanceof FailoverMode) {
            if (redundantUris.length == 0) {

                ServerRedundancyTypeNode redundancyNode = endpoint.getServerRedundancyNode();
                redundantUris = getRedundantUriArray(redundancyNode);
            }
        } else if (!wasFailoverLoadedFromConfig) {
            final Map.Entry<RedundancySupport, String[]> redundancySupport = redundancySupport(endpoint, currentUri, redundantUris);
            switch (redundancySupport.getKey()) {
                case HotAndMirrored:
                case Hot:
                case Warm:
                case Cold:
                    controller = appContext.getBean(ColdFailover.class);
                    break;
                default:
                    controller = appContext.getBean(NoFailover.class);
            }
            redundantUris = redundancySupport.getValue();
        }
        log.info("Using redundancy mode {}, redundant URIs {}.", controller.getClass().getName(), redundantUris);
        controller.initialize(endpoint, redundantUris);
    }

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    @Override
    public void stop() {
        if (controller != null) {
            controller.stop();
        }
        else {
            log.info("No Controller.");
        }
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        return controller.subscribe(groupsWithDefinitions);
    }

    @Override
    public boolean unsubscribe(ItemDefinition definition) {
        return controller.unsubscribe(definition);
    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        return controller.read(nodeId);
    }

    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return controller.callMethod(definition, arg);
    }

    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        return controller.write(nodeId, value);
    }

    private boolean loadedFailoverFromConfigurationSuccessfully() {
        final String customRedundancyMode = config.getRedundancyMode();
        if (customRedundancyMode == null || customRedundancyMode.isEmpty()) {
            return false;
        }
        try {
            final Class<?> redundancyMode = Class.forName(customRedundancyMode);
            if (Controller.class.isAssignableFrom(redundancyMode)) {
                controller = (Controller) appContext.getBean(redundancyMode);
                return true;
            }
            log.error("The redundancy mode in the application properties {} must implement the FailoverProxy interface. Proceeding without.", config.getRedundancyMode());
        } catch (ClassNotFoundException e) {
            log.error("The redundancy mode in the application properties {} could not be resolved. Proceeding without.", config.getRedundancyMode());
        } catch (BeansException e) {
            log.error("The redundancy mode in the application properties {} must be a Spring Bean. Proceeding without.", config.getRedundancyMode());
        }
        return false;
    }

    private Map.Entry<RedundancySupport, String[]> redundancySupport(Endpoint endpoint, String uri, String[] redundantUris) throws OPCUAException {
        endpoint.initialize(uri);
        RedundancySupport mode = RedundancySupport.None;
        String[] redundantUriArray = new String[0];
        try {
            final ServerRedundancyTypeNode redundancyNode = endpoint.getServerRedundancyNode();
            mode = redundancyNode.getRedundancySupport()
                    .thenApply(m -> (m == null) ? RedundancySupport.None : m)
                    .join();
            if (mode != RedundancySupport.None && mode != RedundancySupport.Transparent && redundantUris.length == 0) {
                redundantUriArray = getRedundantUriArray(redundancyNode);
            }
        } catch (CompletionException e) {
            log.error("An exception occurred when reading the server's redundancy information. Proceeding without setting up redundancy.", e);
        }
        return Map.entry(mode, redundantUriArray);
    }

    private String[] getRedundantUriArray(ServerRedundancyTypeNode redundancyNode) {
        String[] redundantUris = loadRedundantUrisFromConfig();
        if (redundantUris.length == 0 && redundancyNode.getClass().isAssignableFrom(NonTransparentRedundancyTypeNode.class)) {
            redundantUris = ((NonTransparentRedundancyTypeNode) redundancyNode).getServerUriArray().join();
            log.info("Loaded redundant uri array from server: {}.", Arrays.toString(redundantUris));
            return redundantUris;
        } else {
            log.info("Could not load redundant uris from server or configuration.");
            return new String[0];
        }
    }

    private String[] loadRedundantUrisFromConfig() {
        final Collection<String> redundantServerUris = config.getRedundantServerUris();
        if (redundantServerUris != null && !redundantServerUris.isEmpty()) {
            log.info("Loaded redundant uris from configuration: {}.", redundantServerUris);
            return redundantServerUris.toArray(String[]::new);
        }
        return new String[0];
    }
}
