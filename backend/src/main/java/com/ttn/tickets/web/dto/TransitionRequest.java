package com.ttn.tickets.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ttn.tickets.web.validation.AllowedValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TransitionRequest(
        @NotBlank @AllowedValues(allowedValues = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"}) String status,
        @Size(max = 5000) String resolutionNotes,
        @Size(max = 5000) String cancellationReason) {
}
