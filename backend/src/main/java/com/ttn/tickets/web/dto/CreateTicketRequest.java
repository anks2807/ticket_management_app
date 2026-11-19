package com.ttn.tickets.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ttn.tickets.web.validation.AllowedValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @AllowedValues(allowedValues = {"LOW", "MEDIUM", "HIGH", "URGENT"}) String priority,
        @NotBlank @AllowedValues(allowedValues = {"BILLING", "TECHNICAL", "ACCOUNT", "SHIPPING", "OTHER"}) String category,
        @Size(max = 100) String assignee) {
}
