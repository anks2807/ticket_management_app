package com.ttn.tickets.application.view;

import java.time.Instant;

import com.ttn.tickets.domain.Category;
import com.ttn.tickets.domain.Priority;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.domain.TicketStatus;

public record TicketSummary(
        String id,
        String title,
        Priority priority,
        Category category,
        String assignee,
        TicketStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketSummary from(Ticket ticket) {
        return new TicketSummary(
                Ticket.formatId(ticket.getId()),
                ticket.getTitle(),
                ticket.getPriority(),
                ticket.getCategory(),
                ticket.getAssignee(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
