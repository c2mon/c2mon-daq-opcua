package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.Collection;

public interface Controller {
    void initialize(String uri, Collection<ISourceDataTag> dataTags) throws CommunicationException, ConfigurationException;
    void reconnect() throws CommunicationException, ConfigurationException;
    void stop() throws CommunicationException;
    void subscribeTags (Collection<ISourceDataTag> dataTags) throws ConfigurationException, CommunicationException;
    String subscribeTag (ISourceDataTag sourceDataTag) throws CommunicationException;
    String removeTag(ISourceDataTag dataTag) throws CommunicationException;
    void refreshAllDataTags() throws CommunicationException;
    void refreshDataTag(ISourceDataTag sourceDataTag) throws CommunicationException;
    boolean isConnected();
    void writeAlive(final OPCHardwareAddress address, final Object value) throws CommunicationException;
    void recreateSubscription(UaSubscription subscription) throws CommunicationException;

    void setEndpoint(Endpoint miloEndpoint);
}
