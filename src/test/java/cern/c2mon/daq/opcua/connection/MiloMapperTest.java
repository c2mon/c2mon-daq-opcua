package cern.c2mon.daq.opcua.connection;

import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import org.eclipse.milo.opcua.stack.core.serialization.UaEnumeration;
import org.eclipse.milo.opcua.stack.core.serialization.UaStructure;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;
import static org.junit.jupiter.api.Assertions.*;

public class MiloMapperTest {

    @Test
    public void variantWithoutDataTypeShouldBeNull() {
        final Variant v = new Variant(null);
        assertNull(MiloMapper.toObject(v));
    }

    @Test
    public void nonLocalVariantShouldBeNull() {
        //Non-local if the Variant contains a UaStructure with a non-0 server index
        final UInteger serverIdx = UInteger.valueOf(3);
        final UaStructure s = () -> new ExpandedNodeId(UShort.valueOf(1), "namespaceUri", UInteger.valueOf(2), serverIdx);
        final Variant v = new Variant(s);
        assertNull(MiloMapper.toObject(v));
    }

    @Test
    public void localVariantShouldBeOK() {
        final UInteger serverIdx = UInteger.valueOf(0);
        final ExpandedNodeId nodeId = new ExpandedNodeId(UShort.valueOf(1), "namespaceUri", UInteger.valueOf(2), serverIdx);
        final Variant v = new Variant(nodeId);
        assertEquals(nodeId, MiloMapper.toObject(v));
    }

    @Test
    public void variantWithPrimitiveTypeShouldReturnValidVariant() {
        final Variant v = new Variant(2);
        final Object o = MiloMapper.toObject(v);
        assertEquals(2, o);
    }

    @Test
    public void variantWithConvertibleEnumShouldReturnProperValue() {
        final UaEnumeration uaEnumeration = () -> 15;
        final Variant v = new Variant(uaEnumeration);
        assertEquals(15, MiloMapper.toObject(v));
    }

    @Test
    public void variantWithArrayShouldReturnArray() {
        final int[] array = {1, 2, 3};
        final Object o = MiloMapper.toObject(new Variant(array));
        assertEquals(array, o);
    }

    @Test
    public void variantsToObjectsShouldReturnValidVariants() {
        final ExpandedNodeId n1 = new ExpandedNodeId(UShort.valueOf(1), "namespaceUri", UInteger.valueOf(2));
        final ExpandedNodeId n2 = new ExpandedNodeId(UShort.valueOf(2), "namespaceUri", UInteger.valueOf(2));
        final ExpandedNodeId n3 = new ExpandedNodeId(UShort.valueOf(3), "namespaceUri", UInteger.valueOf(2));
        final Object[] objects = MiloMapper.toObject(new Variant(n1), new Variant(n2), new Variant(n3));
        assertTrue(Arrays.asList(objects).containsAll(Arrays.asList(n1, n2, n3)));
    }

    @Test
    public void variantsToObjectsShouldReturnOnlyValidVariants() {
        final ExpandedNodeId n1 = new ExpandedNodeId(UShort.valueOf(1), "namespaceUri", UInteger.valueOf(2));
        final ExpandedNodeId n2 = new ExpandedNodeId(UShort.valueOf(2), "namespaceUri", UInteger.valueOf(2));
        final Object[] objects = MiloMapper.toObject(new Variant(n1), new Variant(n2), new Variant(null));
        assertEquals(2, objects.length);
    }

    @Test
    public void goodStatusCodeShouldBeOK() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(Good_CallAgain));
        assertEquals(SourceDataTagQualityCode.OK, dataTagQuality.getQualityCode());
    }


    @Test
    public void nullStatusCodeShouldBeUnknown() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(null);
        assertEquals(SourceDataTagQualityCode.UNKNOWN, dataTagQuality.getQualityCode());
    }

    @Test
    public void outOfBoundsStatusCodeShouldBeOutOfBounds() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(Bad_OutOfRange));
        assertEquals(SourceDataTagQualityCode.OUT_OF_BOUNDS, dataTagQuality.getQualityCode());
    }

    @Test
    public void dataUnavailableCodeShouldBeUnavailable() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(Bad_DataUnavailable));
        assertEquals(SourceDataTagQualityCode.DATA_UNAVAILABLE, dataTagQuality.getQualityCode());
    }

    @Test
    public void configurationIssueShouldBeIncorrectNativeAddress() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(Bad_NodeIdInvalid));
        assertEquals(SourceDataTagQualityCode.INCORRECT_NATIVE_ADDRESS, dataTagQuality.getQualityCode());
    }

    @Test
    public void corruptedShouldBeCorrupted() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(Uncertain_EngineeringUnitsExceeded));
        assertEquals(SourceDataTagQualityCode.VALUE_CORRUPTED, dataTagQuality.getQualityCode());
    }

    @Test
    public void typeMismatchShouldBeUnsupported() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(Bad_TypeMismatch));
        assertEquals(SourceDataTagQualityCode.UNSUPPORTED_TYPE, dataTagQuality.getQualityCode());
    }

    @Test
    public void anyOtherShouldBeUnknown() {
        final SourceDataTagQuality dataTagQuality = MiloMapper.getDataTagQuality(new StatusCode(-1));
        assertEquals(SourceDataTagQualityCode.UNKNOWN, dataTagQuality.getQualityCode());
    }

}
