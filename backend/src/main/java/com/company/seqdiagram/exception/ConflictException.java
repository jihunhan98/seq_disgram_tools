package com.company.seqdiagram.exception;

/** A unique value (username, employee number) is already taken. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
