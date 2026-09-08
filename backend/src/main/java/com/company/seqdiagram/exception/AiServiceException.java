package com.company.seqdiagram.exception;

/** Raised when the in-house AI API is unreachable, misconfigured or unusable. */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
