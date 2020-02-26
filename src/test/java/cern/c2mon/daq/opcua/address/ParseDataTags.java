package cern.c2mon.daq.opcua.address;

import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ParseDataTags {

    List<String> dataTagsStrings = Arrays.asList(
            "                        <DataTagAddress>\n" +
                    "                        <HardwareAddress class=\"cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl\">\n" +
                    "                            <namespace>0</namespace>\n" +
                    "                            <opc-item-name>RandomUnsignedInt32</opc-item-name>\n" +
                    "                            <command-pulse-length>0</command-pulse-length>\n" +
                    "                            <address-type>STRING</address-type>\n" +
                    "                            <command-type>CLASSIC</command-type>\n" +
                    "                        </HardwareAddress>\n" +
                    "                        <time-to-live>9999999</time-to-live>\n" +
                    "                        <value-deadband-type>1</value-deadband-type>\n" +
                    "                        <value-deadband>1.0</value-deadband>\n" +
                    "                        <priority>7</priority>\n" +
                    "                        <guaranteed-delivery>true</guaranteed-delivery>\n" +
                    "                    </DataTagAddress>",
            "                    <DataTagAddress>\n" +
                    "                        <HardwareAddress class=\"cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl\">\n" +
                    "                            <namespace>0</namespace>\n" +
                    "                            <opc-item-name>INVALID</opc-item-name>\n" +
                    "                            <command-pulse-length>0</command-pulse-length>\n" +
                    "                            <address-type>STRING</address-type>\n" +
                    "                            <command-type>CLASSIC</command-type>\n" +
                    "                        </HardwareAddress>\n" +
                    "                        <time-to-live>9999999</time-to-live>\n" +
                    "                        <value-deadband-type>1</value-deadband-type>\n" +
                    "                        <value-deadband>1.0</value-deadband>\n" +
                    "                        <priority>7</priority>\n" +
                    "                        <guaranteed-delivery>true</guaranteed-delivery>\n" +
                    "                    </DataTagAddress>");

    List<ISourceDataTag> dataTags;

    @Test
    public void setUp() {
        ArrayList<DataTagAddress> tagAddresses = new ArrayList<>();
        dataTagsStrings.forEach(s -> tagAddresses.add(DataTagAddress.fromConfigXML(s)));
        dataTags = tagAddresses.stream()
                .map(dataTagAddress -> new SourceDataTag(1L, "test", false, (short) 0, null, dataTagAddress))
                .collect(Collectors.toList());
        System.out.println(dataTags);
    }

}
