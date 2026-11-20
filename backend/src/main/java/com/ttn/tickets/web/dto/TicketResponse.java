package com.ttn.tickets.web.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttn.tickets.application.view.CommentView;
import com.ttn.tickets.application.view.TicketDetails;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TicketResponse(
        String id,
        String title,
        String description,
        String priority,
        String category,
        String assignee,
        String status,
        List<String> allowedTransitions,
        String resolutionNotes,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments) {

    public static TicketResponse from(TicketDetails details) {
        List<CommentResponse> comments = details.comments().stream()
                .map(CommentResponse::from)
                .toList();
        List<String> allowed = details.allowedTransitions().stream()
                .map(Enum::name)
                .toList();
        return new TicketResponse(
                details.id(),
                details.title(),
                details.description(),
                details.priority().name(),
                details.category().name(),
                details.assignee(),
                details.status().name(),
                allowed,
                details.resolutionNotes(),
                details.cancellationReason(),
                details.createdAt(),
                details.updatedAt(),
                comments);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommentResponse(String author, String text, Instant createdAt) {

        static CommentResponse from(CommentView comment) {
            return new CommentResponse(comment.author(), comment.text(), comment.createdAt());
        }
    }
}
