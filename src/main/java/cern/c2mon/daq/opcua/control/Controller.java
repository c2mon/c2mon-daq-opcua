package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.Collection;

public interface Controller {
    void initialize(String uri, Collection<ISourceDataTag> dataTags) throws ConfigurationException;
    void stop();
    void subscribeTags (Collection<ISourceDataTag> dataTags) throws ConfigurationException;
    void refreshAllDataTags();
    void refreshDataTag(ISourceDataTag sourceDataTag);
    void subscribe (EndpointListener listener);
    boolean isConnected();
    void writeAlive(final OPCHardwareAddress address, final Object value);
    void recreateSubscription(UaSubscription subscription);

    void setWrapper(Endpoint wrapper);
}
