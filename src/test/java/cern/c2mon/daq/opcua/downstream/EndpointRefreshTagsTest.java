package cern.c2mon.daq.opcua.downstream;

import org.junit.jupiter.api.Test;

import java.util.Collections;

public class EndpointRefreshTagsTest extends EndpointTestBase {

    @Test
    public void refreshTagsShould () {
        endpoint.refreshDataTags(Collections.singletonList(tag1));

    }
}
