/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.connection;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_ArgumentsMissing;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_BoundNotFound;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_BoundNotSupported;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_DataEncodingInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_DataLost;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_DataUnavailable;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_DeadbandFilterInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_IndexRangeNoData;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_MethodInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NoData;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NoDataAvailable;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NodeClassInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NodeIdInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NodeIdUnknown;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NotFound;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NotReadable;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NotSupported;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NotWritable;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_OutOfRange;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_ParentNodeIdInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_ServiceUnsupported;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_SourceNodeIdInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_TargetNodeIdInvalid;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_TypeMismatch;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_ViewIdUnknown;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Uncertain_EngineeringUnitsExceeded;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Uncertain_InitialValue;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Uncertain_NoCommunicationLastUsableValue;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Uncertain_NotAllNodesAvailable;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Uncertain_SensorNotAccurate;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Uncertain_SubstituteValue;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.milo.opcua.stack.core.serialization.UaEnumeration;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.eclipse.milo.opcua.stack.core.util.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import cern.c2mon.daq.opcua.config.TimeRecordMode;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This utility class provides a collection of functions mapping Milo- or OPCUA specific Java classes into a format that
 * is interpretable for C2MON.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MiloMapper {

    /** Status Codes indicating a node ID supplied by an incorrect hardware address */
    private static final Collection<Long> INCORRECT_NATIVE_ADDRESS = ImmutableSet.<Long>builder().add(
            Bad_NodeIdInvalid,
            Bad_NodeIdUnknown,
            Bad_ParentNodeIdInvalid,
            Bad_SourceNodeIdInvalid,
            Bad_TargetNodeIdInvalid,
            Bad_BoundNotSupported,
            Bad_BoundNotFound,
            Bad_ServiceUnsupported,
            Bad_NotSupported,
            Bad_ViewIdUnknown,
            Bad_NodeClassInvalid,
            Bad_MethodInvalid,
            Bad_ArgumentsMissing,
            Bad_DeadbandFilterInvalid).build();

    private static final Collection<Long> DATA_UNAVAILABLE = ImmutableSet.<Long>builder().add(
            Bad_DataLost,
            Bad_DataUnavailable,
            Bad_NoDataAvailable,
            Bad_NoData,
            Bad_NotReadable,
            Bad_NotWritable,
            Bad_NotFound,
            Bad_IndexRangeNoData,
            Uncertain_InitialValue,
            Uncertain_NoCommunicationLastUsableValue,
            Uncertain_NotAllNodesAvailable,
            Uncertain_SubstituteValue).build();

    private static final Collection<Long> OUT_OF_BOUNDS = ImmutableSet.<Long>builder().add(
            Bad_OutOfRange).build();

    private static final Collection<Long> UNSUPPORTED_TYPE = ImmutableSet.<Long>builder().add(
            Bad_TypeMismatch,
            Bad_DataEncodingInvalid).build();

    private static final Collection<Long> VALUE_CORRUPTED = ImmutableSet.<Long>builder().add(
            Uncertain_SensorNotAccurate,
            Uncertain_EngineeringUnitsExceeded).build();


    /**
     * Represents a {@link StatusCode} as a {@link SourceDataTagQualityCode}
     * @param statusCode the status code returned by the Eclipse Milo client
     * @return a quality code that can be associated with a C2MON {@link cern.c2mon.shared.common.datatag.ISourceDataTag}.
     */
    public static SourceDataTagQuality getDataTagQuality (StatusCode statusCode) {
        SourceDataTagQualityCode tagCode = SourceDataTagQualityCode.UNKNOWN;
        if (statusCode == null) {
            return new SourceDataTagQuality(tagCode, "No status code was passed with the value update");
        } else if (statusCode.isGood()) {
            tagCode = SourceDataTagQualityCode.OK;
        } else if (OUT_OF_BOUNDS.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.OUT_OF_BOUNDS;
        } else if (DATA_UNAVAILABLE.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.DATA_UNAVAILABLE;
        } else if (INCORRECT_NATIVE_ADDRESS.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.INCORRECT_NATIVE_ADDRESS;
        } else if (VALUE_CORRUPTED.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.VALUE_CORRUPTED;
        } else if (UNSUPPORTED_TYPE.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.UNSUPPORTED_TYPE;
        }
        return new SourceDataTagQuality(tagCode, statusCode.toString());
    }

    /**
     * Extracts the actual object from the list of {@link Variant}s that are returned by the Milo client.
     * @param variants the variants wrap objects returned by the Milo client. They can be output arguments returned by a
     *                 call to an OPC UA method node, represent a {@link org.eclipse.milo.opcua.stack.core.types.builtin.DataValue}
     *                 update of a subscription, or other
     * @return the POJOs extracted from the Variants
     */
    public static Object[] toObject (Variant... variants) {
        return Stream.of(variants)
                .map(MiloMapper::toObject)
                .filter(Objects::nonNull)
                .toArray();
    }

    /**
     * Extracts the actual object from a {@link Variant} returned by the Milo client.
     * @param variant the variant wraps an object returned by the Milo client. It can an the output argument returned by
     *                a call to an OPC UA method node, represent a {@link org.eclipse.milo.opcua.stack.core.types.builtin.DataValue}
     *                update of a subscription, or other
     * @return the POJO extracted from the variant
     */
    public static Object toObject (Variant variant) {
        final Optional<ExpandedNodeId> dataType = variant.getDataType();
        if (!dataType.isPresent()) {
            log.info("The variant {} did not contain a data type and cannot be processed.", variant);
            return null;
        }
        final Optional<NodeId> nodeId = dataType.get().local(null);
        if (!nodeId.isPresent()) {
            log.error("The NodeID {} resides in another server. A Namespace Table must be provided to extract it.", dataType.get().toString());
            return null;
        }
        final Class<?> objectClass = TypeUtil.getBackingClass(nodeId.get());
        if (objectClass == null) {
            log.error("The backing object class was not recognized by the Milo OPC UA stack and cannot be processed.");
            return null;
        }
        return variant.getValue() instanceof UaEnumeration ? ((UaEnumeration) variant.getValue()).getValue() : variant.getValue();
    }

    private static final Logger VALUE_UPDATE_LOGGER = LoggerFactory.getLogger("ValueUpdateLogger");
    
    /**
     * Maps a DataValue into a C2MON ValueUpdate.
     * @param value the DataValue received from the Milo client
     * @param mode  the timestamp included in the DataValue that should be used to create the ValueUpdate
     * @return a ValueUpdate containg the value included in the DataValue as well as the appropriate timestamp, if any.
     */
    public static ValueUpdate toValueUpdate (DataValue value, TimeRecordMode mode) {
        if (value == null) {
            VALUE_UPDATE_LOGGER.debug("Discarded a null value update");
            return null;
        }
        ValueUpdate valueUpdate = new ValueUpdate(MiloMapper.toObject(value.getValue()));
        final Long sourceTime = value.getSourceTime() == null ? null : value.getSourceTime().getJavaTime();
        final Long serverTime = value.getServerTime() == null ? null : value.getServerTime().getJavaTime();
        final Long recordedTime = mode.getTime(sourceTime, serverTime);
        if (recordedTime != null) {
            valueUpdate.setSourceTimestamp(recordedTime);
        }
        return valueUpdate;
    }

    /**
     * Maps the ValueDeadbandType constants in {@link ValueDeadbandType} to the OPC UA {@link DeadbandType} (@see <a
     * href="https://reference.opcfoundation.org/v104/Core/docs/Part4/7.17.2">UA Part 4, 7.17.2</a>). If the {@link
     * ValueDeadbandType} concerns the PROCESS, then value filtering is regarded by the DAQ Core.
     * @param c2monDeadbandType the value deadband as a {@link ValueDeadbandType} constant.
     * @return the OPC UA DeadbandType corresponding to the c2monDeadbandType.
     */
    public static DeadbandType toDeadbandType (int c2monDeadbandType) {
        switch (ValueDeadbandType.getValueDeadbandType(c2monDeadbandType)) {
            case EQUIPMENT_ABSOLUTE:
                return DeadbandType.Absolute;
            case EQUIPMENT_RELATIVE:
                return DeadbandType.Percent;
            default:
                return DeadbandType.None;
        }
    }

}
