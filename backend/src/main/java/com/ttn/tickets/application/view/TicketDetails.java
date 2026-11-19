package com.ttn.tickets.application.view;

import java.time.Instant;
import java.util.List;

import com.ttn.tickets.domain.Category;
import com.ttn.tickets.domain.Priority;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.domain.TicketStatus;

public record TicketDetails(
        String id,
        String title,
        String description,
        Priority priority,
        Category category,
        String assignee,
        TicketStatus status,
        String resolutionNotes,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        List<CommentView> comments) {

    public static TicketDetails from(Ticket ticket) {
        List<CommentView> comments = ticket.getComments().stream()
                .map(comment -> new CommentView(comment.getAuthor(), comment.getText(), comment.getCreatedAt()))
                .toList();
        return new TicketDetails(
                Ticket.formatId(ticket.getId()),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getCategory(),
                ticket.getAssignee(),
                ticket.getStatus(),
                ticket.getResolutionNotes(),
                ticket.getCancellationReason(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                comments);
    }
}
