package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;

import java.util.Collection;
import java.util.Map;

/**
 * The {@link TagSubscriptionReader} allows to map in between {@link ItemDefinition}s and {@link ISourceDataTag} IDs, as
 * well as granting read access to their subscription status via {@link SubscriptionGroup}s.
 */
public interface TagSubscriptionReader {

    /**
     * Returns all configured {@link ItemDefinition}s and their associated {@link ISourceDataTag} IDs. All {@link
     * ItemDefinition}s are returned, irrelevant of their presence in a {@link SubscriptionGroup}.
     * @return a Map of all configured {@link ItemDefinition}s and their associated {@link ISourceDataTag} IDs.
     */
    Map<Long, ItemDefinition> getTagIdDefinitionMap();

    /**
     * Gets a {@link SubscriptionGroup} with a publish interval equal to the timeDeadband, or creates a new one if none
     * yet exists.
     * @param timeDeadband to equal the publish interval of the group
     * @return the existing or newly created {@link SubscriptionGroup}
     */
    SubscriptionGroup getGroup(int timeDeadband);

    /**
     * Returns all currently known {@link SubscriptionGroup}s. Usually called by the {@link
     * cern.c2mon.daq.opcua.connection.Endpoint} to recreate the state on the previously active server after a
     * failover.
     * @return all currently known {@link SubscriptionGroup}s
     */
    Collection<SubscriptionGroup> getGroups();

    /**
     * Returns the {@link ItemDefinition} associated with the tagId, or null if no {@link ItemDefinition} is associated
     * with the tagId.
     * @param tagId the tag ID whose associated {@link ItemDefinition} is to be returned
     * @return the {@link ItemDefinition} to which the tagId is mapped, or null if no {@link ItemDefinition} is
     * associated with the tagId
     */
    ItemDefinition getDefinition(long tagId);

    /**
     * Returns the tagId associated with the {@link ItemDefinition} identified by the clientHandle, or null if
     * no tagId is associated with the clientHandle.
     * @param clientHandle the client handle identifying the {@link ItemDefinition} whose associated tagId is to be
     *                     returned
     * @return the tagId associated with the clientHandle
     */
    Long getTagId(int clientHandle);

    /**
     * Returns the {@link ItemDefinition} associated with the tagId, or created a new one {@link ItemDefinition} if none
     * is yet associated with the tagId.
     * @param tag the {@link ISourceDataTag} whose associated {@link ItemDefinition} is to be returned, or for whom a
     *            new {@link ItemDefinition} shall be created
     * @return the {@link ItemDefinition} to which the tagId is mapped, or the newly created {@link ItemDefinition} for
     * the tag.
     * @throws ConfigurationException if the tag's hardware address has an incorrect type.
     */
    ItemDefinition getOrCreateDefinition(ISourceDataTag tag) throws ConfigurationException;
}

