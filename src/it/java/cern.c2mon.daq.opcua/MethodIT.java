package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.CommandRunner;
import cern.c2mon.daq.opcua.control.ControlDelegate;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * There are no method nodes on the Simulation Engine or the Microsoft Edge Test servers, hence testing separately.
 */
public class MethodIT {

    static AppConfig config = TestUtils.createDefaultConfig();
    static ControlDelegate delegate;

    @BeforeAll
    public static void setUpEndpoint() throws ConfigurationException, CommunicationException {
        // Not testing security here, so just connect without security
        SecurityModule p = new SecurityModule(config, new NoSecurityCertifier(), new NoSecurityCertifier(), new NoSecurityCertifier());
        final MiloEndpoint wrapper = new MiloEndpoint(p);
        wrapper.initialize("opc.tcp://milo.digitalpetri.com:62541/milo");
        delegate = new ControlDelegate(TestUtils.createDefaultConfig(), null, new CommandRunner(wrapper));
    }

    @Test
    public void executeMethodWithoutParentReturnsProperResult() throws EqCommandTagException {

        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);
        final SourceCommandTagValue value = new SourceCommandTagValue(tag.getId(), tag.getName(), 0L, (short) 0, 4.0, Double.class.getName());
        final String s = delegate.runCommand(tag, value);
        assertEquals("2.0", s);

    }

    @Test
    public void executeMethodWithParentReturnsProperResult() throws EqCommandTagException {
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods");
        hwAddress.setOpcRedundantItemName("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);
        final SourceCommandTagValue value = new SourceCommandTagValue(tag.getId(), tag.getName(), 0L, (short) 0, 4.0, Double.class.getName());
        final String s = delegate.runCommand(tag, value);
        assertEquals("2.0", s);
    }
}
