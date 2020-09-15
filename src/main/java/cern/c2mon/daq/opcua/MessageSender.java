package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.AllArgsConstructor;

/**
 * Handles communication with the DAQ Core's {@link IEquipmentMessageSender}
 */
public interface MessageSender {

    /**
     * A representation of equipment states and descriptions.
     */
    @AllArgsConstructor
    enum EquipmentState {
        OK("Successfully connected"),
        CONNECTION_FAILED("Cannot establish connection to the server"),
        CONNECTION_LOST("Connection to server has been lost. Reconnecting...");
        /** A description of the state of the equipment and connection */
        public final String message;
    }

    /**
     * Initialize the EndpointListener with the IEquipmentMessageSender instance
     * @param sender the sender to notify of events
     */
    void initialize(IEquipmentMessageSender sender);

    /**
     * Updates the value of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} with the ID tagId
     * @param tagId the id of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} whose value to update
     * @param quality the {@link SourceDataTagQuality} of the updated value
     * @param valueUpdate the {@link ValueUpdate} to send to the {@link cern.c2mon.shared.common.datatag.ISourceDataTag}
     */
    void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate);

    /**
     * Notifies the {@link IEquipmentMessageSender} that a tagId could not be subscribed, or that a bad reading was obtained
     * @param tagId the id of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} to update to invalid
     * @param quality the quality of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag}
     */
    void onTagInvalid(long  tagId, final SourceDataTagQuality quality);

    /**
     * Send an update to the configured aliveTag
     */
    void onAlive();

    /**
     * Updates the state of equipment and connection.
     * @param state the state to update
     */
    void onEquipmentStateUpdate(EquipmentState state);

}
