package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;

public class CertificateBuilderException extends RuntimeException {


    @AllArgsConstructor
    public enum Cause {
        RSA_KEYPAIR("Could not generate RSA Key Pair"),
        CERTIFICATE_BUILDER("Could not build certificate");

        public final String message;

    }

    public CertificateBuilderException(final CertificateBuilderException.Cause type, final Exception e) {
        super(type.message, e);
    }
}
