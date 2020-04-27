package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
public abstract class ItemDefinition {

    private final NodeId address;
    private final NodeId redundantAddress;
    /**
     * equal to the client Handle of a monitoredItem returned by a Milo subscription
     */
    private final UInteger clientHandle;
    private static final AtomicInteger clientHandles = new AtomicInteger();

    @SneakyThrows
    public static DataTagDefinition of(final ISourceDataTag tag) {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new DataTagDefinition(tag, toNodeId(opcAddress), toRedundantNodeId(opcAddress));
    }

    @SneakyThrows
    public static CommandTagDefinition of(final ISourceCommandTag tag) {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new CommandTagDefinition(tag, toNodeId(opcAddress), toRedundantNodeId(opcAddress));
    }

    public static NodeId toNodeId(OPCHardwareAddress opcAddress) {
        return new NodeId(opcAddress.getNamespaceId(), opcAddress.getOPCItemName());
    }

    protected static OPCHardwareAddress extractOpcAddress(HardwareAddress address) throws ConfigurationException {
        if (!(address instanceof OPCHardwareAddress)) {
            throw new ConfigurationException(ConfigurationException.Cause.HARDWARE_ADDRESS_UNKNOWN);
        }
        return (OPCHardwareAddress) address;
    }

    protected static NodeId toRedundantNodeId(OPCHardwareAddress opcAddress) {
        String redundantOPCItemName = opcAddress.getOpcRedundantItemName();
        if (redundantOPCItemName == null || redundantOPCItemName.trim().equals("")) return null;
        else return new NodeId(opcAddress.getNamespaceId(), redundantOPCItemName);
    }

    protected ItemDefinition(NodeId address, NodeId redundantAddress) {
        this.address = address;
        this.redundantAddress = redundantAddress;
        this.clientHandle = UInteger.valueOf(clientHandles.getAndIncrement());
    }

    protected abstract Long getTagId();

    /**
     * The objects are considered equal if their tags have the same id.
     *
     * @param o The object to compare to.
     * @return true if the objects equal else false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemDefinition that = (ItemDefinition) o;
        return getTagId().equals(that.getTagId());
    }

    /**
     * Returns the hash code of this object.
     *
     * @return The hash code of this object which equals the hash code of its
     * ID.
     */
    @Override
    public int hashCode() {
        return getTagId().hashCode();
    }
}
