package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface Controller {

    /**
     * Connect to an OPC UA server.
     *
     * @param uri the URI of the OPC UA server to connect to
     * @throws CommunicationException if an error not related to authentication configuration occurs on connecting to
     *                                the OPC UA server.
     * @throws ConfigurationException if it is not possible to connect to any of the the OPC UA server's endpoints with
     *                                the given authentication configuration settings.
     */
    void connect(String uri) throws OPCUAException;

    void reconnect();

    void stop();

    void subscribeTags(Collection<ISourceDataTag> dataTags) throws ConfigurationException, CommunicationException;

    CompletableFuture<Boolean> subscribeTag(ISourceDataTag sourceDataTag);

    CompletableFuture<Boolean> removeTag(ISourceDataTag dataTag);

    void refreshAllDataTags();

    void refreshDataTag(ISourceDataTag sourceDataTag);

    boolean isConnected();

    CompletableFuture<Void> writeAlive(final OPCHardwareAddress address, final Object value);

    CompletableFuture<Void> recreateSubscription(UaSubscription subscription);

    void setEndpoint(Endpoint miloEndpoint);
}
