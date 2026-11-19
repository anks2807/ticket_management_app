package com.ttn.tickets.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AddCommentRequest(
        @NotBlank @Size(max = 100) String author,
        @NotBlank @Size(max = 5000) String text) {
}
