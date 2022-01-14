package io.neo9.scaler.access.exceptions;

public class InterruptedDownscaleException extends RuntimeException {

	public InterruptedDownscaleException(String message) {
		super(message);
	}

	public InterruptedDownscaleException(String message, Throwable cause) {
		super(message, cause);
	}

}
