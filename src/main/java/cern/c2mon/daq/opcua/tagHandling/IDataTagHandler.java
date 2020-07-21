package cern.c2mon.daq.opcua.tagHandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;

import java.util.Collection;

/**
 * The Controller acts as an interface in between The {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}'s mapping of data
 * points as SourceDataTags, and the {@link cern.c2mon.daq.opcua.connection.Endpoint}'s mapping of points in the address space
 * using uint clientHandles.
 */
public interface IDataTagHandler {

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

    void reset();
}
