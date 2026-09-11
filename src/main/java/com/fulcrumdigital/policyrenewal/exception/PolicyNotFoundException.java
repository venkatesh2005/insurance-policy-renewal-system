package com.fulcrumdigital.policyrenewal.exception;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String policyNumber) {
        super("Policy not found: " + policyNumber);
    }
}