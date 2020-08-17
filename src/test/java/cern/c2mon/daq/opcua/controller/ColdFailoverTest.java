package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import org.junit.jupiter.api.BeforeEach;

public class ColdFailoverTest {

    ColdFailover coldFailover;

    @BeforeEach
    public void setUp() {
        final AppConfigProperties config = TestUtils.createDefaultConfig();

        coldFailover = new ColdFailover(config);
    }
}
