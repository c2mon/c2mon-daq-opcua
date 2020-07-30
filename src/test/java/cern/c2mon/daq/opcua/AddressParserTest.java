package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.config.AddressParser;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class AddressParserTest {

    AppConfigProperties config;

    @BeforeEach
    public void setUp() {
        config = TestUtils.createDefaultConfig();
    }

    @Test
    public void parserShouldReturnStringArrayOfAddresses() throws ConfigurationException {
        Collection<String> expected = Arrays.asList("opc.tcp://test" , "opc.tcp://test2");
        String addressString = "URI=" + StringUtils.join(expected, ",");
        final Collection<String> actual = AddressParser.parse(addressString, config);
        assertEquals(StringUtils.join(expected, ","), StringUtils.join(actual, ","));
    }

    @Test
    public void parserWithAdditionalPropertiesShouldReturnStringArrayOfAddresses() throws ConfigurationException {
        Collection<String> expected = Arrays.asList("opc.tcp://test" , "opc.tcp://test2");
        String addressString = "URI=" + StringUtils.join(expected, ",") + ";testProperties";
        final Collection<String> actual = AddressParser.parse(addressString, config);
        assertEquals(StringUtils.join(expected, ","), StringUtils.join(actual, ","));
    }

    @Test
    public void parserShouldReturnOpcTcp() throws Exception {
        String expected = "opc.tcp://test";
        final Collection<String> actual = AddressParser.parse(expected + ";testProperties", config);
        assertEquals(expected, StringUtils.join(actual, ","));
    }


    @Test
    public void parserShouldReturnHttp() throws Exception {
        String expected = "http://test";
        final Collection<String> actual = AddressParser.parse(expected + ";testProperties", config);
        assertEquals(expected, StringUtils.join(actual, ","));
    }

    @Test
    public void parserShouldReturnHttpAndOpcTcp() throws Exception {
        String expectedHttp = "http://test";
        String expectedOpc = "opc.tcp://test";
        final Collection<String> actual = AddressParser.parse(expectedHttp + "," + expectedOpc + ";testProperties", config);
        assertEquals(expectedHttp+","+expectedOpc, StringUtils.join(actual, ","));
    }

    @Test
    public void parserShouldNotReturnAddressesOtherThanOpcTcpOrHttp() throws Exception {
        String expected = "opc.tcp://test";
        final Collection<String> actual = AddressParser.parse("URI=ptf://abc," + expected + ";testProperties", config);
        assertEquals(expected, StringUtils.join(actual, ","));
    }

    @Test
    public void parserWithEmptyURIShouldThrowError() {
        String addressString = "URI=;serverRetryTimeout=123;testProperties";
        assertThrows(ConfigurationException.class, () -> AddressParser.parse(addressString, config), ExceptionContext.URI_MISSING.getMessage());
    }

    @Test
    public void parserWithNoURIShouldThrowError() {
        String addressString = "testProperties";
        assertThrows(ConfigurationException.class, () -> AddressParser.parse(addressString, config), ExceptionContext.URI_MISSING.getMessage());
    }

    @Test
    public void parserWithWrongURIShouldThrowError() {
        String addressString = "URI=abc;testProperties";
        assertThrows(ConfigurationException.class, () -> AddressParser.parse(addressString, config), ExceptionContext.URI_SYNTAX.getMessage());
    }


    @Test
    public void parserWithBadPropertiesShouldNotChangeConfig() throws Exception {
        final String expected = config.toString();
        AddressParser.parse("URI=opc.tcp://test;testProperties", config);
        assertEquals(expected, config.toString());
    }

    @Test
    public void parserWithMatchingPropertyShouldChangeConfig() throws Exception {
        final long notExpected = config.getRestartDelay();
        AddressParser.parse("URI=opc.tcp://test;restartDelay=1000", config);
        assertNotEquals(notExpected, config.getRestartDelay());
    }

    @Test
    public void parserWithMatchingPropertyShouldOverwriteConfig() throws Exception {
        final long expected = -20;
        AddressParser.parse("URI=opc.tcp://test;restartDelay=-20", config);
        assertEquals(expected, config.getRestartDelay());
    }

    @Test
    public void parserWithMatchingPropertyShouldSetPKIConfig() throws Exception {
        final String expected = "xxx";
        AddressParser.parse("URI=opc.tcp://test;restartDelay=1000;pki.certificatePath="+expected, config);
        assertEquals(expected, config.getPkiConfig().getCertificatePath());
    }

    @Test
    public void parserWithMatchingPropertyShouldChangeKeystoreConfig() throws Exception {
        final String notExpected = config.getKeystore().toString();
        AddressParser.parse("URI=opc.tcp://test;restartDelay=1000;keystore.path=xxx", config);
        assertNotEquals(notExpected, config.getKeystore().toString());
    }

    @Test
    public void parserWithMatchingPropertyShouldSetKeystoreConfig() throws Exception {
        final String expected = "xxx";
        AddressParser.parse("URI=opc.tcp://test;restartDelay=1000;keystore.path="+expected, config);
        assertEquals(expected, config.getKeystore().getPath());
    }
}