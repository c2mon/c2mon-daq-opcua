package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An OPC UA subscription groups several data points on the server, here represented by {@link ItemDefinition}s. All
 * items within a subscription share a publish interval. The Client is notified of updates once per publish interval.
 * The {@link SubscriptionGroup} follows the same concept, and allows the {@link ConcreteController} to maintain an
 * application-level rather than server-level overview over currently subscribed items. It stores a reference to {@link
 * ISourceDataTag}s along with the subscribed {@link ItemDefinition}s for fast mapping.
 */
@Getter
@RequiredArgsConstructor
public class SubscriptionGroup {

    private final Map<Long, ItemDefinition> tagIds = new ConcurrentHashMap<>();
    private final int publishInterval;

    /**
     * Find the amount of items within the subscription.
     * @return the amount of items within the subscription
     */
    public int size () {
        return this.tagIds.size();
    }

    /**
     * Adds a successfully subscribed {@link ItemDefinition} to the group
     * @param tagId          the tagID associated with the {@link ItemDefinition} to add
     * @param itemDefinition the item to add
     */
    public void add (final long tagId, final ItemDefinition itemDefinition) {
        this.tagIds.putIfAbsent(tagId, itemDefinition);
    }

    /**
     * Removes the {@link ItemDefinition} associated with the tagId from the subscription, if it was subscribed-
     * @param tagId the identifier of the {@link ISourceDataTag} associated with the {@link ItemDefinition}
     * @return true if the tagId was previously subscribed.
     */
    public boolean remove (long tagId) {
        return this.tagIds.remove(tagId) != null;
    }

    /**
     * Returns true if the group contains the {@link ItemDefinition} associated with the tagId
     * @param tagId associated with the {@link ItemDefinition} whose presence is to be tested
     * @return true is the group contains an {@link ItemDefinition} associated with the tagId.
     */
    public boolean contains (long tagId) {
        return tagIds.containsKey(tagId);
    }
}
