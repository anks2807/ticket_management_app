package com.ttn.tickets.web.error;

import java.util.List;

public class ValidationFailedException extends RuntimeException {

    private final List<ApiFieldError> errors;

    public ValidationFailedException(List<ApiFieldError> errors) {
        super("Validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<ApiFieldError> errors() {
        return errors;
    }
}
