package com.ttn.tickets.web.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(String field, String code, String message, List<String> allowedValues) {
}
