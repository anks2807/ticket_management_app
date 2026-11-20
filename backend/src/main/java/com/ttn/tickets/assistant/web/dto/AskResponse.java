package com.ttn.tickets.assistant.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskResponse(
        String outcome,
        String answer,
        List<TicketSourceDto> sources) {
}
