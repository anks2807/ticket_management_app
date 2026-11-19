package com.ttn.tickets.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ttn.tickets.application.TicketCommandService;
import com.ttn.tickets.application.TicketNotFoundException;
import com.ttn.tickets.application.TicketQueryService;
import com.ttn.tickets.application.view.CommentView;
import com.ttn.tickets.application.view.TicketDetails;
import com.ttn.tickets.application.view.TicketPage;
import com.ttn.tickets.domain.Category;
import com.ttn.tickets.domain.Priority;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.domain.TicketStatus;
import com.ttn.tickets.web.dto.AddCommentRequest;
import com.ttn.tickets.web.dto.CreateTicketRequest;
import com.ttn.tickets.web.dto.EditTicketRequest;
import com.ttn.tickets.web.dto.TicketResponse;
import com.ttn.tickets.web.dto.TransitionRequest;
import com.ttn.tickets.web.error.ApiFieldError;
import com.ttn.tickets.web.error.ValidationFailedException;
import com.ttn.tickets.web.validation.AllowedValues;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final String MERGE_PATCH = "application/merge-patch+json";
    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final List<String> CATEGORIES = List.of("BILLING", "TECHNICAL", "ACCOUNT", "SHIPPING", "OTHER");

    private final TicketCommandService commands;
    private final TicketQueryService queries;

    public TicketController(TicketCommandService commands, TicketQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    public TicketPage list(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "status", required = false)
            @AllowedValues(allowedValues = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"}) String status,
            @RequestParam(name = "page", required = false) @Min(0) Integer page) {
        int pageIndex = page == null ? 0 : page;
        if ((keyword == null || keyword.isBlank()) && (status == null || status.isBlank())) {
            return queries.list(pageIndex);
        }
        TicketStatus parsed = status == null || status.isBlank() ? null : TicketStatus.valueOf(status);
        return queries.search(keyword, parsed, pageIndex);
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketDetails created = commands.create(
                request.title(),
                request.description(),
                request.priority(),
                request.category(),
                request.assignee());
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(TicketResponse.from(created));
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(@PathVariable String ticketId) {
        return TicketResponse.from(queries.getById(requireTicketId(ticketId)));
    }

    @PatchMapping(path = "/{ticketId}", consumes = {MERGE_PATCH, MediaType.APPLICATION_JSON_VALUE})
    public TicketResponse edit(@PathVariable String ticketId, @RequestBody EditTicketRequest request) {
        Ticket.Changes changes = toChanges(request);
        return TicketResponse.from(commands.edit(requireTicketId(ticketId), changes));
    }

    @PostMapping("/{ticketId}/comments")
    public ResponseEntity<TicketResponse.CommentResponse> addComment(@PathVariable String ticketId,
            @Valid @RequestBody AddCommentRequest request) {
        CommentView comment = commands.addComment(requireTicketId(ticketId), request.author(), request.text());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TicketResponse.CommentResponse(comment.author(), comment.text(), comment.createdAt()));
    }

    @PostMapping("/{ticketId}/transitions")
    public TicketResponse transition(@PathVariable String ticketId, @Valid @RequestBody TransitionRequest request) {
        rejectStrayAndMissing(request);
        TicketStatus target = TicketStatus.valueOf(request.status());
        TicketDetails updated = commands.transition(requireTicketId(ticketId), target, notesOrReason(request, target));
        return TicketResponse.from(updated);
    }

    private static Ticket.Changes toChanges(EditTicketRequest request) {
        if (request.isEmpty()) {
            throw new ValidationFailedException(List.of(new ApiFieldError(
                    "body", "EMPTY", "At least one editable field is required", null)));
        }
        List<ApiFieldError> errors = new ArrayList<>();
        String title = requiredText(request, "title", 200, errors);
        String description = requiredText(request, "description", 5000, errors);
        String priority = allowedText(request, "priority", PRIORITIES, errors);
        String category = allowedText(request, "category", CATEGORIES, errors);
        AssigneeChange assignee = assignee(request, errors);
        String resolutionNotes = requiredText(request, "resolutionNotes", 5000, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new Ticket.Changes(
                title,
                description,
                priority == null ? null : Priority.valueOf(priority),
                category == null ? null : Category.valueOf(category),
                assignee.clear(),
                assignee.value(),
                resolutionNotes);
    }

    private static String requiredText(EditTicketRequest request, String field, int max, List<ApiFieldError> errors) {
        if (!request.has(field)) {
            return null;
        }
        if (request.isNull(field)) {
            errors.add(new ApiFieldError(field, "REQUIRED", field + " must not be null", null));
            return null;
        }
        if (!request.isTextual(field)) {
            errors.add(new ApiFieldError(field, "INVALID", field + " must be a string", null));
            return null;
        }
        String value = request.text(field);
        if (value.isBlank()) {
            errors.add(new ApiFieldError(field, "BLANK", field + " must not be blank", null));
            return null;
        }
        if (value.length() > max) {
            errors.add(new ApiFieldError(field, "TOO_LONG", field + " is too long", null));
            return null;
        }
        return value;
    }

    private static String allowedText(EditTicketRequest request, String field, List<String> allowed,
            List<ApiFieldError> errors) {
        if (!request.has(field)) {
            return null;
        }
        if (request.isNull(field)) {
            errors.add(new ApiFieldError(field, "REQUIRED", field + " must not be null", null));
            return null;
        }
        if (!request.isTextual(field)) {
            errors.add(new ApiFieldError(field, "INVALID", field + " must be a string", null));
            return null;
        }
        String value = request.text(field);
        if (!allowed.contains(value)) {
            errors.add(new ApiFieldError(field, "NOT_ALLOWED", field + " is not an allowed value", allowed));
            return null;
        }
        return value;
    }

    private static AssigneeChange assignee(EditTicketRequest request, List<ApiFieldError> errors) {
        if (!request.has("assignee")) {
            return new AssigneeChange(false, null);
        }
        if (request.isNull("assignee")) {
            return new AssigneeChange(true, null);
        }
        if (!request.isTextual("assignee")) {
            errors.add(new ApiFieldError("assignee", "INVALID", "assignee must be a string or null", null));
            return new AssigneeChange(false, null);
        }
        String value = request.text("assignee");
        if (value.length() > 100) {
            errors.add(new ApiFieldError("assignee", "TOO_LONG", "assignee is too long", null));
            return new AssigneeChange(false, null);
        }
        if (value.isBlank()) {
            return new AssigneeChange(false, null);
        }
        return new AssigneeChange(false, value);
    }

    private record AssigneeChange(boolean clear, String value) {
    }

    private static void rejectStrayAndMissing(TransitionRequest request) {
        TicketStatus target = TicketStatus.valueOf(request.status());
        List<ApiFieldError> errors = new ArrayList<>();
        if (request.resolutionNotes() != null && target != TicketStatus.RESOLVED) {
            errors.add(new ApiFieldError("resolutionNotes", "NOT_APPLICABLE",
                    "resolutionNotes is only allowed when status is RESOLVED", null));
        }
        if (request.cancellationReason() != null && target != TicketStatus.CANCELLED) {
            errors.add(new ApiFieldError("cancellationReason", "NOT_APPLICABLE",
                    "cancellationReason is only allowed when status is CANCELLED", null));
        }
        if (target == TicketStatus.RESOLVED && isBlank(request.resolutionNotes())) {
            errors.add(new ApiFieldError("resolutionNotes", request.resolutionNotes() == null ? "REQUIRED" : "BLANK",
                    "resolutionNotes is required when status is RESOLVED", null));
        }
        if (target == TicketStatus.CANCELLED && isBlank(request.cancellationReason())) {
            errors.add(new ApiFieldError("cancellationReason",
                    request.cancellationReason() == null ? "REQUIRED" : "BLANK",
                    "cancellationReason is required when status is CANCELLED", null));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }

    private static String notesOrReason(TransitionRequest request, TicketStatus target) {
        if (target == TicketStatus.RESOLVED) {
            return request.resolutionNotes();
        }
        if (target == TicketStatus.CANCELLED) {
            return request.cancellationReason();
        }
        return null;
    }

    private static long requireTicketId(String ticketId) {
        Long id = Ticket.parseId(ticketId);
        if (id == null) {
            throw new TicketNotFoundException(ticketId);
        }
        return id;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
