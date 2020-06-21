package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.ISourceDataTag;

import java.util.Collection;

/**
 * The Controller acts as an interface in between The {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}'s mapping of data
 * points as SourceDataTags, and the {@link cern.c2mon.daq.opcua.connection.Endpoint}'s mapping of points in the address space
 * using uint clientHandles.
 */
public interface Controller {

    /**
     * Connects to an OPC UA server.
     * @param uri the URI of the OPC UA server to connect to.
     * @throws OPCUAException       of type {@link CommunicationException} if an error not related to authentication
     *                              configuration occurs on connecting to the OPC UA server, and of type {@link
     *                              ConfigurationException} if it is not possible to connect to any of the the OPC UA
     *                              server's endpoints with the given authentication configuration settings.
     */
    void connect(String uri) throws OPCUAException;

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    void stop();

    boolean isStopped();

    /**
     * Subscribes to the OPC UA nodes corresponding to the data tags on the server.
     * @param dataTags the collection of ISourceDataTags to subscribe to.
     * @throws ConfigurationException if the collection of tags was empty.
     */
    void subscribeTags(Collection<ISourceDataTag> dataTags) throws ConfigurationException;

    /**
     * Subscribes to the OPC UA node corresponding to one data tag on the server.
     * @param sourceDataTag the ISourceDataTag to subscribe to.
     * @return true if the Tag could be subscribed successfully.
     */
    boolean subscribeTag(ISourceDataTag sourceDataTag);

    /**
     * Removes a Tag from the internal configuration and if already subscribed from the OPC UA subscription. It the
     * subscription is then empty, it is deleted along with the monitored item.
     * @param dataTag the tag to remove.
     * @return true if the tag was previously known.
     */
    boolean removeTag(ISourceDataTag dataTag);

    /**
     * Reads the current values from the server for all subscribed data tags.
     */
    void refreshAllDataTags();

    /**
     * Reads the sourceDataTag's current value from the server
     * @param sourceDataTag the tag whose current value to read
     */
    void refreshDataTag(ISourceDataTag sourceDataTag);

    /**
     * Called when a subscription could not be automatically transferred in between sessions. In this case, the     *
     * subscription is recreated from scratch.
     * @param publishInterval the publishInterval of the subscription of the old session which must be recreated.
     * @throws OPCUAException of type {@link CommunicationException} if the subscription could not be recreated due to
     *                        communication difficulty, and of type {@link ConfigurationException} if the subscription
     *                        can not be mapped to current DataTags, and therefore cannot be recreated.
     */
    void recreateSubscription(Endpoint endpoint, int publishInterval) throws OPCUAException;

    boolean subscribeToGroup(SubscriptionGroup group, Collection<ItemDefinition> definitions);
}
