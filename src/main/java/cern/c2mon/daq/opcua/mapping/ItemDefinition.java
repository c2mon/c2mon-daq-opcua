package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Slf4j
public class ItemDefinition {

    private final NodeId nodeId;
    private final NodeId methodNodeId;
    private final int timeDeadband;
    private final float valueDeadband;
    private final short valueDeadbandType;
    /**
     * equal to the client Handle of a monitoredItem returned by a Milo subscription
     */
    private final UInteger clientHandle;
    private static final AtomicInteger clientHandles = new AtomicInteger();

    public static ItemDefinition of(NodeId nodeId) {
        return new ItemDefinition(nodeId, null);
    }

    public static ItemDefinition of(final ISourceDataTag tag) {
        try {
            OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
            return new ItemDefinition(tag, toNodeId(opcAddress), toRedundantNodeId(opcAddress));
        } catch (ConfigurationException e) {
            log.error("Configuration error, should be unreachable!", e);
            return null;
        }
    }

    public static ItemDefinition of(final ISourceCommandTag tag) {
        try {
            OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
            return new ItemDefinition(toNodeId(opcAddress), toRedundantNodeId(opcAddress));
        } catch (ConfigurationException e) {
            log.error("Configuration error, should be unreachable!", e);
            return null;
        }
    }

    public static NodeId toNodeId(final ISourceCommandTag tag) {
        try {
            OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
            return toNodeId(opcAddress);
        } catch (ConfigurationException e) {
            log.error("Configuration error, should be unreachable!", e);
            return null;
        }
    }

    public static NodeId toNodeId(OPCHardwareAddress opcAddress) {
        return new NodeId(opcAddress.getNamespaceId(), opcAddress.getOPCItemName());
    }

    private static NodeId toRedundantNodeId(OPCHardwareAddress opcAddress) {
        String redundantOPCItemName = opcAddress.getOpcRedundantItemName();
        if (redundantOPCItemName == null || redundantOPCItemName.trim().equals("")) return null;
        else return new NodeId(opcAddress.getNamespaceId(), redundantOPCItemName);
    }

    protected static OPCHardwareAddress extractOpcAddress(HardwareAddress address) throws ConfigurationException {
        if (!(address instanceof OPCHardwareAddress)) {
            throw new ConfigurationException(ExceptionContext.HARDWARE_ADDRESS_UNKNOWN);
        }
        return (OPCHardwareAddress) address;
    }

    protected ItemDefinition(NodeId nodeId, NodeId methodNodeId) {
        this(nodeId, methodNodeId, 0, 0, (short) 0);
    }

    protected ItemDefinition(final ISourceDataTag tag, final NodeId nodeId, final NodeId methodNodeId) {
        this(nodeId, methodNodeId, tag.getTimeDeadband(), tag.getValueDeadband(), tag.getValueDeadbandType());
    }

    private ItemDefinition(NodeId nodeId, NodeId methodNodeId, int timeDeadband, float valueDeadband, short valueDeadbandType) {
        this.nodeId = nodeId;
        this.methodNodeId = methodNodeId;
        this.clientHandle = UInteger.valueOf(clientHandles.getAndIncrement());
        this.timeDeadband = timeDeadband;
        this.valueDeadband = valueDeadband;
        this.valueDeadbandType = valueDeadbandType;
    }
}
