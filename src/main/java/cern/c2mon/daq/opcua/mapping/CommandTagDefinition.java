package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.command.ISourceCommandTag;
import lombok.Getter;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

@Getter
public class CommandTagDefinition extends ItemDefinition {

    /**
     * the c2mon tag
     */
    private final ISourceCommandTag tag;

    protected Long getTagId() {
        return tag.getId();
    }

    protected CommandTagDefinition(final ISourceCommandTag tag, final NodeId address, final NodeId redundantAddress) {
        super(address, redundantAddress);
        this.tag = tag;
    }

}
