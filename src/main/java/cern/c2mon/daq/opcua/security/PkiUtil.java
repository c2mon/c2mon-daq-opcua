/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import io.netty.util.internal.StringUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A utility class to load private and public RSA keys as well certificates from PEM-encoded files
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PkiUtil {

    /**
     * Check whether the given configuration contains all values required to load a certificate and key pair from a pfx
     * file, and the appropriate files are present.
     * @param keystoreConfig the configuration to check for completeness
     * @return whether the given configuration contains all values required to load a certificate and key pair from a
     * pfx file
     */
    public static boolean isKeystoreConfigured(AppConfigProperties.KeystoreConfig keystoreConfig) {
        return keystoreConfig != null &&
                Stream.of(keystoreConfig.getType(), keystoreConfig.getPath(), keystoreConfig.getAlias()).noneMatch(StringUtil::isNullOrEmpty) &&
                Paths.get(keystoreConfig.getPath()).toFile().exists();
    }

    /**
     * Check whether the given configuration contains all values required to load a certificate and private key from
     * their respective PEM files, and that all files are present.
     * @param pkiConfig the configuration to check for completeness
     * @return whether the given configuration contains all values required to load a certificate and private key from
     * their respective PEM file
     */
    public static boolean isPkiConfigured(AppConfigProperties.PKIConfig pkiConfig) {
        boolean isConfigComplete = pkiConfig != null &&
                !StringUtil.isNullOrEmpty(pkiConfig.getCertificatePath()) &&
                !StringUtil.isNullOrEmpty(pkiConfig.getPrivateKeyPath());
        return isConfigComplete &&
                Paths.get(pkiConfig.getCertificatePath()).toFile().exists() &&
                Paths.get(pkiConfig.getPrivateKeyPath()).toFile().exists();
    }

    /**
     * Load a {@link X509Certificate} and {@link KeyPair} from a {@link KeyStore} file
     * @param config the keystore configuration
     * @return A Map.Entry of the certificate and key pair
     * @throws ConfigurationException if certificate or keypair cannot be loaded
     */
    public static Map.Entry<X509Certificate, KeyPair> loadFromPfx(AppConfigProperties.KeystoreConfig config) throws ConfigurationException {
        if (isKeystoreConfigured(config)) {
            try (InputStream in = Files.newInputStream(Paths.get(config.getPath()))) {
                final KeyStore keyStore = KeyStore.getInstance(config.getType());
                String pwdString = config.getPassword() == null ? "" : config.getPassword();
                final char[] pwd = pwdString.toCharArray();
                keyStore.load(in, pwd);
                Key privateKey = keyStore.getKey(config.getAlias(), pwd);
                if (privateKey instanceof PrivateKey) {
                    X509Certificate certificate = (X509Certificate) keyStore.getCertificate(config.getAlias());
                    PublicKey publicKey = certificate.getPublicKey();
                    KeyPair keyPair = new KeyPair(publicKey, (PrivateKey) privateKey);
                    return new AbstractMap.SimpleEntry<>(certificate, keyPair);
                }
            } catch (IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException e) {
                throw new ConfigurationException(ExceptionContext.SECURITY, e);
            }
        }
        throw new ConfigurationException(ExceptionContext.SECURITY);
    }

    /**
     * Load an X.509 Certificate and a PKCS8-encoded private key from a PEM file
     * @param config the configuration required to load certificate and keypair
     * @return A Map.Entry of the certificate and key pair consisting of the loaded private key and the certificate's
     * public key
     * @throws ConfigurationException if either of the files cannot be found, the private key or certificate cannot be
     *                                loaded, are have an unsupported encording or similar.
     */
    public static Map.Entry<X509Certificate, KeyPair> loadFromPki(AppConfigProperties.PKIConfig config) throws ConfigurationException {
        if (isPkiConfigured(config)) {
            final PrivateKey privateKey = loadPrivateKey(config.getPrivateKeyPath());
            final X509Certificate certificate = loadCertificate(config.getCertificatePath());
            KeyPair keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
            return new AbstractMap.SimpleEntry<>(certificate, keyPair);
        }
        throw new ConfigurationException(ExceptionContext.SECURITY);
    }

    private static PrivateKey loadPrivateKey(String filename) throws ConfigurationException {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            byte[] content = getPemObject(filename).getContent();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
            return factory.generatePrivate(privKeySpec);
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            throw new ConfigurationException(ExceptionContext.SECURITY, e);
        }
    }

    private static X509Certificate loadCertificate(String filepath) throws ConfigurationException {
        try (FileInputStream is = new FileInputStream(filepath)) {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            return (X509Certificate) fact.generateCertificate(is);
        } catch (IOException | CertificateException e) {
            throw new ConfigurationException(ExceptionContext.SECURITY, e);
        }
    }

    private static PemObject getPemObject(String filename) throws IOException {
        try (FileInputStream is = new FileInputStream(filename);
             InputStreamReader isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
             PemReader pemReader = new PemReader(isReader)) {
            final PemObject pemObject = pemReader.readPemObject();
            if (pemObject == null || pemObject.getContent() == null) {
                throw new IOException();
            } else {
                return pemObject;
            }
        }
    }
}
