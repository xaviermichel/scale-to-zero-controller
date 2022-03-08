package io.neo9.scaler.access.exceptions;

public class InterruptedProxyForwardException extends RuntimeException {

    public InterruptedProxyForwardException(String message) {
        super(message);
    }

    public InterruptedProxyForwardException(String message, Throwable cause) {
        super(message, cause);
    }

}
