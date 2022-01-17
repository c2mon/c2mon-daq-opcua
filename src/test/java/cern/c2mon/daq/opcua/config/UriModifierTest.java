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
package cern.c2mon.daq.opcua.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cern.c2mon.daq.opcua.config.AppConfigProperties.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UriModifierTest {

    AppConfigProperties config;
    UriModifier modifier;

    String discovery = "http://discovery:3000/path";

    @BeforeEach
    public void setUp() {
        config = builder()
                .hostSubstitutionMode(HostSubstitutionMode.SUBSTITUTE_GLOBAL)
                .globalHostName("test.tf")
                .portSubstitutionMode(PortSubstitutionMode.GLOBAL)
                .globalPort(20)
                .build();
        modifier = new UriModifier(config);
    }

    @Test
    public void noSubstitutionAndNullHostConfiguredShouldReturnUnchanged() {
        config.setHostSubstitutionMode(HostSubstitutionMode.NONE);
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://aaaa/path/query?test", "http://aaaa/path/query?test");
    }

    @Test
    public void uriWithIllegalCharactersShouldReturnUnchanged() {
        updateAndAssertEquals(discovery, "opc.tcp://!§\"$$§%\"!%", "opc.tcp://!§\"$$§%\"!%");
    }

    @Test
    public void localNullShouldReturnNull() {
        updateAndAssertEquals(discovery, null, null);
    }

    @Test
    public void discoveryNullShouldReturnLocal() {
        updateAndAssertEquals(null, "local", "local");
    }

    @Test
    public void unsetGlobalUriShouldReturnLocal() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        config.setGlobalHostName(null);
        updateAndAssertEquals(discovery, "http://aaaa/path/query?test", "http://aaaa/path/query?test");
    }

    @Test
    public void hostShouldBeSubstitutedByGlobalUriIfConfigured() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://aaaa/path/query?test", "http://test.tf/path/query?test");
    }
    @Test
    public void uriWithUnderscoreShouldBeRecognized() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://a_aaa/path/query?test", "http://test.tf/path/query?test");
    }


    @Test
    public void substitutingSameHostShouldReturnLocal() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://test.tf/path/query?test", "http://test.tf/path/query?test");
    }

    @Test
    public void longHostShouldBeSubstitutedIfConfigured() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://a.b.cde/path/query?test", "http://test.tf/path/query?test");
    }

    @Test
    public void userShouldNotBeSubstituted() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://user@a.b.cde/path/query?test", "http://user@test.tf/path/query?test");
    }

    @Test
    public void userWithPwdShouldNotBeSubstituted() {
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://user:pwd@a.b.cde/path/query?test", "http://user:pwd@test.tf/path/query?test");
    }

    @Test
    public void globalHostShouldBeAppendedIfConfigured() {
        config.setHostSubstitutionMode(HostSubstitutionMode.APPEND_GLOBAL);
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "http://aaaa/path/query?test", "http://aaaa.test.tf/path/query?test");
    }

    @Test
    public void substitutingHostShouldBeSkippedIfEmpty() {
        config.setGlobalHostName("");
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "opc.tcp://cccc:2000/path", "opc.tcp://cccc:2000/path");
    }

    @Test
    public void substitutingHostShouldBeSkippedIfNull() {
        config.setGlobalHostName(null);
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "opc.tcp://cccc:2000/path", "opc.tcp://cccc:2000/path");
    }

    @Test
    public void portShouldBeReplacedIfConfigured() {
        config.setHostSubstitutionMode(HostSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "opc.tcp://cccc:2000/path", "opc.tcp://cccc:20/path");
    }

    @Test
    public void portShouldBeAddedIfNotPresent() {
        config.setHostSubstitutionMode(HostSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "opc.tcp://cccc/path", "opc.tcp://cccc:20/path");
    }


    @Test
    public void localPortShouldBeReplacedIfConfigured() {
        config.setHostSubstitutionMode(HostSubstitutionMode.NONE);
        config.setPortSubstitutionMode(PortSubstitutionMode.LOCAL);
        updateAndAssertEquals(discovery, "opc.tcp://cccc:333/path", "opc.tcp://cccc:3000/path");
    }

    @Test
    public void localPortShouldBeSkippedIfNotInURI() {
        config.setHostSubstitutionMode(HostSubstitutionMode.NONE);
        config.setPortSubstitutionMode(PortSubstitutionMode.LOCAL);
        updateAndAssertEquals("opc.tcp://discovery/path", "opc.tcp://cccc/path", "opc.tcp://cccc/path");
    }

    @Test
    public void localPortShouldRemainIfNotInDiscoveryURIButInLocalURI() {
        config.setHostSubstitutionMode(HostSubstitutionMode.NONE);
        config.setPortSubstitutionMode(PortSubstitutionMode.LOCAL);
        updateAndAssertEquals("opc.tcp://discovery/path", "opc.tcp://cccc:2000/path", "opc.tcp://cccc:2000/path");
    }


    @Test
    public void portAndHostShouldBeReplacedIfConfigured() {
        updateAndAssertEquals(discovery, "opc.tcp://cccc:2000/path", "opc.tcp://test.tf:20/path");
    }

    @Test
    public void discoveryHostShouldBeReplacedIfConfigured() {
        config.setHostSubstitutionMode(HostSubstitutionMode.SUBSTITUTE_LOCAL);
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals(discovery, "opc.tcp://test.tf:2000/path", "opc.tcp://discovery:2000/path");
    }

    @Test
    public void discoveryHostShouldBeSkippedIfMalformed() {
        config.setHostSubstitutionMode(HostSubstitutionMode.SUBSTITUTE_LOCAL);
        config.setPortSubstitutionMode(PortSubstitutionMode.NONE);
        updateAndAssertEquals("baduri", "opc.tcp://test.tf:2000/path", "opc.tcp://test.tf:2000/path");
    }

    private void updateAndAssertEquals(String discovery, String local, String expected) {
        final String global = modifier.updateEndpointUrl(discovery, local);
        assertEquals(expected, global);
    }
}
