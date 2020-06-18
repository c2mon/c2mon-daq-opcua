package cern.c2mon.daq.opcua.security;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;

/**
 * A utility class to load private and public RSA keys as well certificates from PEM-encoded files
 */
public class PkiUtil {

    /**
     * Load an X.509 Certificate from a PEM file
     * @param filepath the path of the PEM file containing the certificate
     * @return the loaded certificate
     * @throws ConfigurationException if the certificate cannot be loaded
     */
    public static X509Certificate loadCertificate(String filepath) throws ConfigurationException {
        try (FileInputStream is = new FileInputStream(filepath)) {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            return (X509Certificate) fact.generateCertificate(is);
        } catch (IOException | CertificateException e) {
            throw new ConfigurationException(ExceptionContext.SECURITY, e);
        }
    }

    /**
     * Load a {@link X509Certificate} and {@link KeyPair} from a {@link KeyStore} file
     * @param config the keystore configuration
     * @return A Map.Entry of the certificate and key pair
     * @throws ConfigurationException if certificate or keypair cannot be loaded
     */
    public static Map.Entry<X509Certificate, KeyPair> loadFromPfx(AppConfig.KeystoreConfig config) throws ConfigurationException {
        try (InputStream in = Files.newInputStream(Paths.get(config.getPath()))) {
            final KeyStore keyStore = KeyStore.getInstance(config.getType());
            keyStore.load(in, config.getPwd().toCharArray());
            Key privateKey = keyStore.getKey(config.getAlias(), config.getPwd().toCharArray());
            if (privateKey instanceof PrivateKey) {
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate(config.getAlias());
                PublicKey publicKey = certificate.getPublicKey();
                KeyPair keyPair = new KeyPair(publicKey, (PrivateKey) privateKey);
                return Map.entry(certificate, keyPair);
            }
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException e) {
            throw new ConfigurationException(ExceptionContext.SECURITY, e);
        }
        throw new ConfigurationException(ExceptionContext.SECURITY);
    }

    /**
     * Load a PKCS8-encoded key from a PEM file.
     * @param filename the path of the PEM file containing the private key.
     * @return the loaded private key.
     * @throws ConfigurationException if the file cannot be found, the private key or keypair cannot be loaded, the file
     *                                is PKCS1-encoded, or similar.
     */
    public static PrivateKey loadPrivateKey(String filename) throws ConfigurationException {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            byte[] content = getPemObject(filename).getContent();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
            return factory.generatePrivate(privKeySpec);
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            throw new ConfigurationException(ExceptionContext.SECURITY, e);
        }
    }

    private static PemObject getPemObject(String filename) throws IOException {
        try (PemReader pemReader = new PemReader(new InputStreamReader(new FileInputStream(filename)))) {
            return pemReader.readPemObject();
        }
    }
}