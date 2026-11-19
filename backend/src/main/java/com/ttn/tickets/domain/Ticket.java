package com.ttn.tickets.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket")
public class Ticket {

    private static final Pattern CANONICAL_ID = Pattern.compile("TKT-[1-9][0-9]*");

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_id_gen")
    @SequenceGenerator(name = "ticket_id_gen", sequenceName = "ticket_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Category category;

    @Column(length = 100)
    private String assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TicketStatus status;

    @Column(name = "resolution_notes", length = 5000)
    private String resolutionNotes;

    @Column(name = "cancellation_reason", length = 5000)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<Comment> comments = new ArrayList<>();

    protected Ticket() {
    }

    public static Ticket create(String title, String description, Priority priority, Category category, String assignee,
            Instant now) {
        if (title == null || title.isBlank() || description == null || description.isBlank() || priority == null
                || category == null || now == null) {
            throw new IllegalArgumentException("title, description, priority, category, and now are required");
        }
        Ticket ticket = new Ticket();
        ticket.title = title;
        ticket.description = description;
        ticket.priority = priority;
        ticket.category = category;
        ticket.assignee = assignee == null || assignee.isBlank() ? null : assignee;
        ticket.status = TicketStatus.OPEN;
        ticket.createdAt = now;
        ticket.updatedAt = now;
        return ticket;
    }

    public void moveTo(TicketStatus target, String notesOrReason, Instant now) {
        if (target == null || now == null) {
            throw new IllegalArgumentException("target and now are required");
        }
        if (!status.canMoveTo(target)) {
            throw new InvalidTransitionException(status, status.allowedTransitions());
        }
        if (target == TicketStatus.RESOLVED) {
            requireText(notesOrReason, "resolution notes are required");
            this.resolutionNotes = notesOrReason;
        } else if (target == TicketStatus.CANCELLED) {
            requireText(notesOrReason, "cancellation reason is required");
            this.cancellationReason = notesOrReason;
        }
        this.status = target;
        this.updatedAt = now;
    }

    public void edit(Changes changes, Instant now) {
        if (changes == null || now == null) {
            throw new IllegalArgumentException("changes and now are required");
        }
        if (status == TicketStatus.CLOSED || status == TicketStatus.CANCELLED) {
            throw new TicketFrozenException(status);
        }
        if (changes.resolutionNotes() != null && resolutionNotes == null) {
            throw new ResolutionNotesNotEditableException(status);
        }
        apply(changes);
        this.updatedAt = now;
    }

    public Comment addComment(String author, String text, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        Comment comment = new Comment(this, author, text, now);
        comments.add(comment);
        this.updatedAt = now;
        return comment;
    }

    public record Changes(
            String title,
            String description,
            Priority priority,
            Category category,
            boolean clearAssignee,
            String assignee,
            String resolutionNotes) {

        public Changes {
            if (clearAssignee && assignee != null) {
                throw new IllegalArgumentException("assignee cannot be both cleared and set");
            }
        }
    }

    public static String formatId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        return "TKT-" + id;
    }

    public static Long parseId(String external) {
        if (external == null || !CANONICAL_ID.matcher(external).matches()) {
            return null;
        }
        return Long.parseLong(external.substring(4));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public Category getCategory() {
        return category;
    }

    public String getAssignee() {
        return assignee;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Comment> getComments() {
        return comments;
    }

    private void apply(Changes changes) {
        if (changes.title() != null) {
            requireText(changes.title(), "title is required");
            this.title = changes.title();
        }
        if (changes.description() != null) {
            requireText(changes.description(), "description is required");
            this.description = changes.description();
        }
        if (changes.priority() != null) {
            this.priority = changes.priority();
        }
        if (changes.category() != null) {
            this.category = changes.category();
        }
        if (changes.clearAssignee()) {
            this.assignee = null;
        } else if (changes.assignee() != null) {
            requireText(changes.assignee(), "assignee must not be blank");
            this.assignee = changes.assignee();
        }
        if (changes.resolutionNotes() != null) {
            requireText(changes.resolutionNotes(), "resolution notes cannot be cleared");
            this.resolutionNotes = changes.resolutionNotes();
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
