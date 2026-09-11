package com.fulcrumdigital.policyrenewal.exception;

public class InvalidPolicyException extends RuntimeException {
    public InvalidPolicyException(String message) {
        super(message);
    }
}