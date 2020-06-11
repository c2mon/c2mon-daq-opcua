package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.connection.EndpointSubscriptionListener;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.CommandRunner;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ColdFailover;
import cern.c2mon.daq.opcua.failover.FailoverProxy;
import cern.c2mon.daq.opcua.failover.FailoverProxyImpl;
import cern.c2mon.daq.opcua.failover.NoFailover;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * There are no method nodes on the Simulation Engine or the Microsoft Edge Test servers, hence testing separately.
 */
public class MethodIT {

    static AppConfig config = TestUtils.createDefaultConfig();
    static CommandRunner runner;

    @BeforeAll
    public static void setUpEndpoint() throws OPCUAException, InterruptedException {
        // Not testing security here, so just connect without security
        SecurityModule p = new SecurityModule(config, new NoSecurityCertifier(), new NoSecurityCertifier(), new NoSecurityCertifier());
        RetryDelegate delegate = new RetryDelegate();
        ReflectionTestUtils.setField(delegate, "maxRetryCount", 3);
        ReflectionTestUtils.setField(delegate, "timeout", 3000);
        ReflectionTestUtils.setField(delegate, "retryDelay", 1000);
        final MiloEndpoint wrapper = new MiloEndpoint(p, new TestListeners.TestListener(), new EndpointSubscriptionListener(), delegate);
        FailoverProxy failover = new FailoverProxyImpl(new NoFailover(), new ColdFailover(), wrapper);
        failover.initialize("opc.tcp://milo.digitalpetri.com:62541/milo");
        runner = new CommandRunner(failover);
    }

    @Test
    public void executeMethodWithoutParentReturnsProperResult() throws EqCommandTagException {

        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);
        final SourceCommandTagValue value = new SourceCommandTagValue(tag.getId(), tag.getName(), 0L, (short) 0, 4.0, Double.class.getName());
        final String s = runner.runCommand(tag, value);
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
        final String s = runner.runCommand(tag, value);
        assertEquals("2.0", s);
    }
}
