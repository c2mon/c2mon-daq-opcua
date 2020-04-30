package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.Endpoint;
import cern.c2mon.daq.opcua.control.EndpointImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * There are no method nodes on the Simulation Engine or the Microsoft Edge Test servers, hence testing separately.
 */
public class MethodIT {

    static AppConfig config = TestUtils.createDefaultConfig();
    static Endpoint endpoint;
    static EventPublisher publisher;

    @BeforeAll
    public static void setUpEndpoint() {
        // Not testing security here, so just connect without security
        SecurityModule p = new SecurityModule(config, new NoSecurityCertifier(), new NoSecurityCertifier(), new NoSecurityCertifier());
        publisher = new EventPublisher();
        endpoint = new EndpointImpl(new MiloClientWrapper(p), new TagSubscriptionMapperImpl(), publisher);
        endpoint.initialize("opc.tcp://milo.digitalpetri.com:62541/milo");
        endpoint.connect(false);

    }

    @Test
    public void executeMethodWithoutParentReturnsProperResult() {

        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);

        final Object[] output = endpoint.executeMethod(tag, 4.0);

        assertEquals(1, output.length);
        assertEquals(2.0, output[0]);

    }

    @Test
    public void executeMethodWithParentReturnsProperResult() {
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods");
        hwAddress.setOpcRedundantItemName("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);

        final Object[] output = endpoint.executeMethod(tag, 4.0);

        assertEquals(1, output.length);
        assertEquals(2.0, output[0]);
    }
}
