package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.DataTagDeadband;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Utility enum for mapping the ValueDeadbandType constants in {@link DataTagDeadband} to the OPC UA deadbandType (@see
 * <a href="https://reference.opcfoundation.org/v104/Core/docs/Part4/7.17.2">UA Part 4, 7.17.2</a>)
 */
@AllArgsConstructor
public enum ValueDeadbandType {
    NONE(0),
    ABSOLUTE(1),
    RELATIVE(2);

    @Getter
    int opcuaValueDeadbandType;

    /**
     * Returns the appropriate deadband filter type for this DAQ instance. If the {@link DataTagDeadband} concerns the
     * PROCESS, then value filtering is regarded by the DAQ Core.
     * @param c2monDeadbandType the value deadband as a {@link DataTagDeadband} constant.
     * @return the DAQ-specific ValueDeadbandType.
     */
    public static ValueDeadbandType of(short c2monDeadbandType) {
        switch (c2monDeadbandType) {
            case DataTagDeadband.DEADBAND_EQUIPMENT_ABSOLUTE:
                return ABSOLUTE;
            case DataTagDeadband.DEADBAND_EQUIPMENT_RELATIVE:
                return RELATIVE;
            default:
                return NONE;
        }
    }
}
