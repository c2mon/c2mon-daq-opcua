package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.connection.MiloMapper;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link ItemDefinition} stores all values required to process an {@link ISourceDataTag} or an {@link
 * ISourceCommandTag} including the {@link NodeId} and Deadband values. Every {@link ItemDefinition} is identified by a
 * unique clientHandle which is used by the {@link TagSubscriptionMapper} to associate it with the tag.
 */
@Getter
@Slf4j
public class ItemDefinition {

    private static final AtomicInteger clientHandles = new AtomicInteger();

    /**
     * Extracted from the primary item name of an {@link OPCHardwareAddress}
     */
    private final NodeId nodeId;

    /**
     * This equals the redundant item name of a {@link OPCHardwareAddress}. If this value is set in an {@link
     * ISourceDataTag}, it is subscribed to equally with the primary nodeId. If it is set on an {@link
     * ISourceCommandTag} of type {@link cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress.COMMAND_TYPE}
     * METHOD, the primary {@link NodeId} is interpreted as referring to the containing object Node, and the
     * methodNodeId as referring to the method Node.
     */
    private final NodeId methodNodeId;
    private final int timeDeadband;
    private final float valueDeadband;
    private final DeadbandType valueDeadbandType;

    /**
     * The {@link ItemDefinition}'s unique identifier, equal to the client Handle of a monitoredItem returned by a Milo
     * subscription
     */
    private final int clientHandle;

    private ItemDefinition (NodeId nodeId, NodeId methodNodeId) {
        this(nodeId, methodNodeId, 0, 0, 0);
    }

    private ItemDefinition (final ISourceDataTag tag, final NodeId nodeId, final NodeId methodNodeId) {
        this(nodeId, methodNodeId, tag.getTimeDeadband(), tag.getValueDeadband(), tag.getValueDeadbandType());
    }

    private ItemDefinition (NodeId nodeId, NodeId methodNodeId, int timeDeadband, float valueDeadband, int valueDeadbandType) {
        this.nodeId = nodeId;
        this.methodNodeId = methodNodeId;
        this.clientHandle = clientHandles.getAndIncrement();
        this.timeDeadband = timeDeadband;
        this.valueDeadband = valueDeadband;
        this.valueDeadbandType = MiloMapper.toDeadbandType(valueDeadbandType);
    }

    /**
     * Returns the NodeId for an {@link ISourceCommandTag}'s primary address.
     * @param tag the {@link ISourceCommandTag} for which to return a NodeId
     * @return the {@link NodeId} for the tag's primary address
     * @throws ConfigurationException if the primary address has an incorrect hardware type.
     */
    public static NodeId getNodeIdForTag (final ISourceCommandTag tag) throws ConfigurationException {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new NodeId(opcAddress.getNamespaceId(), opcAddress.getOPCItemName());
    }

    /**
     * Creates a new {@link ItemDefinition} from a {@link NodeId}. Used by the {@link ConcreteController}s when
     * subscribing to default Nodes to supervise the server health
     * @param nodeId the {@link NodeId} for which to create an {@link ItemDefinition}
     * @return the newly created {@link ItemDefinition}
     */
    public static ItemDefinition of (NodeId nodeId) {
        return new ItemDefinition(nodeId, null);
    }

    /**
     * Stores the relevant information contained in an {@link ISourceDataTag} into a corresponding {@link
     * ItemDefinition} including the {@link NodeId}s and Deadband values.
     * @param tag the {@link ISourceDataTag} for which an associated {@link ItemDefinition} shall be created
     * @return the newly created {@link ItemDefinition}
     * @throws ConfigurationException if the tag has an address of incorrect type
     */
    public static ItemDefinition of (final ISourceDataTag tag) throws ConfigurationException {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new ItemDefinition(tag, new NodeId(opcAddress.getNamespaceId(), opcAddress.getOPCItemName()), toRedundantNodeId(opcAddress));
    }

    /**
     * Stores the relevant information contained in an {@link ISourceCommandTag} into a corresponding {@link
     * ItemDefinition} including primary and method {@link NodeId}s.
     * @param tag the {@link ISourceCommandTag} for which an associated {@link ItemDefinition} shall be created
     * @return the newly created {@link ItemDefinition}
     * @throws ConfigurationException if the tag has an address of incorrect type
     */
    public static ItemDefinition of (final ISourceCommandTag tag) throws ConfigurationException {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new ItemDefinition(new NodeId(opcAddress.getNamespaceId(), opcAddress.getOPCItemName()), toRedundantNodeId(opcAddress));
    }

    private static OPCHardwareAddress extractOpcAddress (HardwareAddress address) throws ConfigurationException {
        if (!(address instanceof OPCHardwareAddress)) {
            throw new ConfigurationException(ExceptionContext.HARDWARE_ADDRESS_TYPE);
        }
        return (OPCHardwareAddress) address;
    }

    private static NodeId toRedundantNodeId (OPCHardwareAddress opcAddress) {
        String redundantOPCItemName = opcAddress.getOpcRedundantItemName();
        return (redundantOPCItemName == null || redundantOPCItemName.trim().isEmpty()) ?
                null : new NodeId(opcAddress.getNamespaceId(), redundantOPCItemName);
    }
}
