package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;

/**
 * A custom wrapper for exceptions occurring during creation of self-signed certificates
 */
public class SecurityProviderException extends Exception {


    /**
     * Stores messages of possible causes for a CertificateBuilderException to be thrown.
     */
    @AllArgsConstructor
    public enum Cause {
        RSA_KEYPAIR("Could not generate RSA Key Pair"),
        CERTIFICATE_BUILDER("Could not build certificate"),
        LOAD_CERTIFICATE("Could not load certificate"),
        STORE_SELFSIGNED("Could not store self signed certificate");

        public final String message;

    }

    public SecurityProviderException(final SecurityProviderException.Cause type, final Exception e) {
        super(type.message, e);
    }
}
