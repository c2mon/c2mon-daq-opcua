package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * This class is responsible for fetching a server's redundancy support information and for delegating to the proper
 * {@link Controller} according to the application properties in {@link AppConfigProperties} and to server
 * capabilities.
 */
@Slf4j
@RequiredArgsConstructor
@Component(value = "failoverProxy")
@Primary
public class FailoverProxyImpl implements FailoverProxy {
    protected final ApplicationContext appContext;
    protected final AppConfigProperties config;
    protected final MessageSender messageSender;

    @Lazy
    protected final NoFailover noFailover;

    protected Controller failoverMode;

    /**
     * Delegated failover handling to an appropriate {@link Controller} according to the application configuration or,
     * secondarily, according to server capabilities. If no redundant Uris are preconfigured, they are read from the
     * server's ServerUriArray node. In case of errors in the configuration and when reading from the server's address
     * space, the FailoverProxy treats the server as not part of a redundant setup.
     * Notify the {@link cern.c2mon.daq.common.IEquipmentMessageSender} if a failure occurs which prevents the DAQ from starting successfully.
     * @param uri the URI of the active server
     * @throws OPCUAException       of type {@link CommunicationException} if an error not related to authentication
     *                              configuration occurs on connecting to the OPC UA server, and of type {@link
     *                              ConfigurationException} if it is not possible to connect to any of the the OPC UA
     *                              server's endpoints with the given authentication configuration settings.
     */
    @Override
    public void connect(String uri) throws OPCUAException {
        try {
            String[] redundantUris;
            if (loadedConfiguredFailoverSuccessfully()) {
                redundantUris = failoverMode instanceof NoFailover ?
                        new String[0] : loadRedundantUris(noFailover.currentEndpoint(), uri);
            } else {
                failoverMode = noFailover;
                final Map.Entry<RedundancySupport, String[]> redundancySupport = redundancySupport(failoverMode.currentEndpoint(), uri);
                switch (redundancySupport.getKey()) {
                    case HotAndMirrored:
                    case Hot:
                    case Warm:
                    case Cold:
                        failoverMode = appContext.getBean(ColdFailover.class);
                        break;
                    default:
                        failoverMode = noFailover;
                }
                redundantUris = redundancySupport.getValue();
            }
            log.info("Using redundancy mode {}, redundant URIs {}.", failoverMode.getClass().getName(), redundantUris);
            failoverMode.initializeMonitoring(uri, noFailover.currentEndpoint(), redundantUris);
        } catch (OPCUAException e) {
            messageSender.onEquipmentStateUpdate(MessageSender.EquipmentState.CONNECTION_FAILED);
            throw e;
        }
    }

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    @Override
    public void stop() {
        if (failoverMode != null) {
            failoverMode.stop();
        }
    }

    /**
     * Fetch the currently active endpoint on which all actions are executed
     * @return the currently active endpoint of a redundant server set.
     */
    @Override
    public Endpoint getActiveEndpoint() {
        return failoverMode.currentEndpoint();
    }

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to
     * disconnect. If the connected server is not within a redundant server set, an empty list is returned.
     */
    @Override
    public void unsubscribeDefinition(ItemDefinition definition) throws OPCUAException {
        failoverMode.unsubscribe(definition);
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribeDefinitions(Collection<ItemDefinition> definitions) {
        return failoverMode.subscribe(definitions);
    }

    private boolean loadedConfiguredFailoverSuccessfully() {
        final String customRedundancyMode = config.getRedundancyMode();
        if (customRedundancyMode == null || customRedundancyMode.isEmpty()) {
            return false;
        }
        try {
            final Class<?> redundancyMode = Class.forName(customRedundancyMode);
            if (Controller.class.isAssignableFrom(redundancyMode)) {
                failoverMode = (Controller) appContext.getBean(redundancyMode);
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

    private String[] loadRedundantUris(Endpoint endpoint, String uri) throws OPCUAException {
        endpoint.initialize(uri);
        String[] redundantUris = loadRedundantUris();
        if (redundantUris.length == 0) {
            final ServerRedundancyTypeNode redundancyNode = endpoint.getServerRedundancyNode();
            redundantUris = getRedundantUriArray(redundancyNode);
        }
        return redundantUris;
    }

    private String[] loadRedundantUris() {
        final Collection<String> redundantServerUris = config.getRedundantServerUris();
        if (redundantServerUris != null && !redundantServerUris.isEmpty()) {
            log.info("Loaded redundant uris from configuration: {}.", redundantServerUris);
            return redundantServerUris.toArray(String[]::new);
        }
        return new String[0];
    }

    private Map.Entry<RedundancySupport, String[]> redundancySupport(Endpoint endpoint, String uri) throws OPCUAException {
        endpoint.initialize(uri);
        RedundancySupport mode = RedundancySupport.None;
        String[] redundantUriArray = new String[0];
        try {
            final ServerRedundancyTypeNode redundancyNode = endpoint.getServerRedundancyNode();
            mode = redundancyNode.getRedundancySupport()
                    .thenApply(m -> (m == null) ? RedundancySupport.None : m)
                    .join();
            if (mode != RedundancySupport.None && mode != RedundancySupport.Transparent) {
                redundantUriArray = getRedundantUriArray(redundancyNode);
            }
        } catch (CompletionException e) {
            log.error("An exception occurred when reading the server's redundancy information. Proceeding without setting up redundancy.", e);
        }
        return Map.entry(mode, redundantUriArray);
    }

    private String[] getRedundantUriArray(ServerRedundancyTypeNode redundancyNode) {
        String[] redundantUris = loadRedundantUris();
        if (redundantUris.length == 0) {
            redundantUris = ((NonTransparentRedundancyTypeNode) redundancyNode).getServerUriArray().join();
            log.info("Loaded redundant uri array from server: {}.", Arrays.toString(redundantUris));
            return redundantUris;
        } else {
            log.info("Could not load redundant uris from server or configuration.");
            return new String[0];
        }
    }
}
