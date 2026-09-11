package com.fulcrumdigital.policyrenewal.exception;

public class DlqEntryNotFoundException extends RuntimeException {
    public DlqEntryNotFoundException(Long id) {
        super("DLQ entry not found: " + id);
    }
}
