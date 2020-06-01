package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

import java.util.Collection;

public interface Controller {

    /**
     * Connects to an OPC UA server.
     * @param uri the URI of the OPC UA server to connect to.
     * @throws OPCUAException       of type {@link CommunicationException} if an error not related to authentication
     *                              configuration occurs on connecting to the OPC UA server, and of type {@link
     *                              ConfigurationException} if it is not possible to connect to any of the the OPC UA
     *                              server's endpoints with the given authentication configuration settings.
     * @throws InterruptedException if the thread was interrupted on execution before the connection could be
     *                              established.
     */
    void connect(String uri) throws OPCUAException, InterruptedException;

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    void stop();

    /**
     * Returns whether the controller has been intentionally shut down.
     * @return true if the controller's stop method has been called, or the initial connection was not successful.
     */
    boolean wasStopped();

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
     * @throws InterruptedException if the thread was interrupted before the tag could be subscribed.
     */
    boolean subscribeTag(ISourceDataTag sourceDataTag) throws InterruptedException;

    /**
     * Removes a Tag from the internal configuration and if already subscribed from the OPC UA subscription. It the
     * subscription is then empty, it is deleted along with the monitored item.
     * @param dataTag the tag to remove.
     * @return true if the tag was previously known.
     * @throws InterruptedException if the thread was interrupted before the tag could be removed.
     */
    boolean removeTag(ISourceDataTag dataTag) throws InterruptedException;

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
     * @param subscription the subscription of the old session which must be recreated.
     * @throws InterruptedException   if the thread was interrupted before the subscription could be recreated.
     * @throws CommunicationException if the subscription could not be recreated due to communication difficulty.
     * @throws ConfigurationException if the subscription can not be mapped to current DataTags, and therefore cannot be
     *                                recreated.
     */
    void recreateSubscription(UaSubscription subscription) throws InterruptedException, CommunicationException, ConfigurationException;
}
