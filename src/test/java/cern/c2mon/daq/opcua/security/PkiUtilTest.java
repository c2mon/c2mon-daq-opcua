/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
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
package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.iotedge.SecurityIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PkiUtilTest {
    AppConfigProperties.KeystoreConfig ksConfig;
    AppConfigProperties.PKIConfig pkiConfig;
    String pfxPath = SecurityIT.class.getClassLoader().getResource("keystore.pfx").getPath();
    String keyPath = SecurityIT.class.getClassLoader().getResource("pkcs8server.key").getPath();
    String certPath = SecurityIT.class.getClassLoader().getResource("server.crt").getPath();

    @BeforeEach
    public void setUp() {
        ksConfig = new AppConfigProperties.KeystoreConfig("PKCS12", pfxPath, "password", "1");
        pkiConfig = new AppConfigProperties.PKIConfig(keyPath, certPath);
    }

    @Test
    public void isPkiConfiguredShouldReturnTrueIfAllValuesAndFilesArePresent() {
        assertTrue(PkiUtil.isPkiConfigured(pkiConfig));
    }

    @Test
    public void isPkiConfiguredShouldReturnFalseIfKeystoreConfigIsNull() {
        assertFalse(PkiUtil.isPkiConfigured(null));
    }

    @Test
    public void isPkiConfiguredShouldReturnFalseIfEitherValueIsNull() {
        final AppConfigProperties.PKIConfig noKey = new AppConfigProperties.PKIConfig(null, certPath);
        final AppConfigProperties.PKIConfig noCert = new AppConfigProperties.PKIConfig(keyPath, null);
        assertFalse(PkiUtil.isPkiConfigured(noKey));
        assertFalse(PkiUtil.isPkiConfigured(noCert));
    }

    @Test
    public void isPkiConfiguredShouldReturnFalseIfEitherFileIsNotPresent() {
        final AppConfigProperties.PKIConfig noKey = new AppConfigProperties.PKIConfig("badPath", certPath);
        final AppConfigProperties.PKIConfig noCert = new AppConfigProperties.PKIConfig(keyPath, "badPath");
        assertFalse(PkiUtil.isPkiConfigured(noKey));
        assertFalse(PkiUtil.isPkiConfigured(noCert));
    }

    @Test
    public void isKeystoreConfiguredShouldReturnTrueIfAllValuesAndFilesArePresent() {
        assertTrue(PkiUtil.isKeystoreConfigured(ksConfig));
    }

    @Test
    public void isKeystoreConfiguredShouldReturnFalseIfKeystoreConfigIsNull() {
        assertFalse(PkiUtil.isKeystoreConfigured(null));
    }

    @Test
    public void isKeystoreConfiguredShouldReturnFalseIfTypePathOrAliasAreNull() {
        final AppConfigProperties.KeystoreConfig noType = new AppConfigProperties.KeystoreConfig(null, pfxPath, "password", "1");
        final AppConfigProperties.KeystoreConfig noPath = new AppConfigProperties.KeystoreConfig("PKCS12", null, "password", "1");
        final AppConfigProperties.KeystoreConfig noAlias = new AppConfigProperties.KeystoreConfig("PKCS12", pfxPath, "password", null);
        assertFalse(PkiUtil.isKeystoreConfigured(noType));
        assertFalse(PkiUtil.isKeystoreConfigured(noPath));
        assertFalse(PkiUtil.isKeystoreConfigured(noAlias));
    }

    @Test
    public void isKeystoreConfiguredShouldReturnFalseIfNoFileExistsAtPath() {
        ksConfig.setPath("xxx");
        assertFalse(PkiUtil.isKeystoreConfigured(ksConfig));
    }


    @Test
    void loadFromPkiShouldReturnPkAndCertIfBothExist() throws ConfigurationException {
        final Map.Entry<X509Certificate, KeyPair> e = PkiUtil.loadFromPki(pkiConfig);
        assertNotNull(e);
        assertNotNull(e.getKey());
        assertNotNull(e.getValue());
    }

    @Test
    void loadFromPkiShouldThrowErrorIfNotConfigured() {
        pkiConfig.setCertificatePath("");
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPki(pkiConfig));
    }

    @Test
    void loadFromPkiShouldThrowErrorIfPrivateKeyIsInvalid() {
        pkiConfig.setPrivateKeyPath(certPath);
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPki(pkiConfig));
    }

    @Test
    void loadFromPkiShouldThrowErrorIfPrivateKeyHasBadFileType() {
        pkiConfig.setPrivateKeyPath(pfxPath);
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPki(pkiConfig));
    }

    @Test
    void loadFromPkiShouldThrowErrorIfCertificateIsInvalid() {
        pkiConfig.setCertificatePath(keyPath);
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPki(pkiConfig));
    }
    @Test
    void loadFromPkiShouldThrowErrorIfCertificateHasBadFileType() {
        pkiConfig.setCertificatePath(pfxPath);
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPki(pkiConfig));
    }

    @Test
    void loadFromPfxShouldReturnPkAndCertIfBothExist() throws ConfigurationException {
        final Map.Entry<X509Certificate, KeyPair> e = PkiUtil.loadFromPfx(ksConfig);
        assertNotNull(e);
        assertNotNull(e.getKey());
        assertNotNull(e.getValue());
    }

    @Test
    void loadFromPfxShouldThrowErrorIfNotConfigured() {
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPfx(null));
    }

    @Test
    void loadFromPkiShouldThrowErrorIfPfxHasInvalidFileType() {
        ksConfig.setPath(keyPath);
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPfx(ksConfig));
    }

    @Test
    void loadFromPkiShouldThrowErrorIfTypeDoesNotMatch() {
        ksConfig.setType("PKCS8");
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPfx(ksConfig));
    }

    @Test
    void loadFromPkiWithInvalidPwdShouldThrowException() {
        ksConfig.setPassword("badpwd");
        assertThrows(ConfigurationException.class, () -> PkiUtil.loadFromPfx(ksConfig));
    }

    @Test
    void loadFromPkiWithoutPwdShouldWorkIfNotRequired() throws ConfigurationException {
        ksConfig.setPassword(null);
        ksConfig.setPath(SecurityIT.class.getClassLoader().getResource("nopwd.pfx").getPath());
        final Map.Entry<X509Certificate, KeyPair> e = PkiUtil.loadFromPfx(ksConfig);
        assertNotNull(e);
        assertNotNull(e.getKey());
        assertNotNull(e.getValue());
    }

}
