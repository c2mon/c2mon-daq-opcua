package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.Endpoint;
import cern.c2mon.daq.opcua.control.EndpointImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.testutils.ServerTestListener;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodIT {

/*    @Test
    public void test() {
        config.setEnableOnDemandCertification(false);
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());

        final EndpointImpl endpoint = new EndpointImpl(new MiloClientWrapper(p), new TagSubscriptionMapperImpl(), new EventPublisher());
        endpoint.initialize("opc.tcp://cs-ccr-mpetb01.cern.ch:4841");
        endpoint.connect(false);
    }*/

    static AppConfig config = TestUtils.createDefaultConfig();
    static Endpoint endpoint;
    static EventPublisher publisher;
    SourceCommandTagValue value;
    CompletableFuture<Map.Entry<StatusCode, Object[]>> methodResponse;
    @BeforeAll
    public static void setUpEndpoint() {
        SecurityModule p = new SecurityModule(config, new NoSecurityCertifier(), new NoSecurityCertifier(), new NoSecurityCertifier());
        publisher = new EventPublisher();
        endpoint = new EndpointImpl(new MiloClientWrapper(p), new TagSubscriptionMapperImpl(), publisher);
        endpoint.initialize("opc.tcp://milo.digitalpetri.com:62541/milo");
        endpoint.connect(false);

    }

    @BeforeEach
    public void setUpTests() {
        value = new SourceCommandTagValue();
        value.setDataType(Double.class.getName());
        value.setValue(4);

        methodResponse = ServerTestListener.subscribeAndReturnListener(publisher).getMethodResponse();
    }

    @Test
    public void writeToMethodNodeWithoutParent() throws ConfigurationException, ExecutionException, InterruptedException {
        // Not testing security here, so skip testing secure endpoints
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);

        endpoint.executeCommand(tag, value);
        final Map.Entry<StatusCode, Object[]> o = methodResponse.get();
        assertEquals(StatusCode.GOOD, o.getKey());
        assertEquals(2.0, o.getValue()[0]);

    }

    @Test
    public void writeToMethodNodeReturnsProperResult() throws ConfigurationException, ExecutionException, InterruptedException {
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Methods");
        hwAddress.setOpcRedundantItemName("Methods/sqrt(x)");
        hwAddress.setNamespace(2);
        hwAddress.setCommandType(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD);
        final ISourceCommandTag tag = new SourceCommandTag(1L, "sqrt", 5000, 5, hwAddress);

        endpoint.executeCommand(tag, value);
        final Map.Entry<StatusCode, Object[]> o = methodResponse.get();
        assertEquals(StatusCode.GOOD, o.getKey());
        assertEquals(2.0, o.getValue()[0]);
    }
}
