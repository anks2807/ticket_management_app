package com.ttn.tickets.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttn.tickets.application.view.CommentView;
import com.ttn.tickets.application.view.TicketDetails;
import com.ttn.tickets.domain.Category;
import com.ttn.tickets.domain.ChangeType;
import com.ttn.tickets.domain.Comment;
import com.ttn.tickets.domain.Priority;
import com.ttn.tickets.domain.RefreshIntent;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.domain.TicketStatus;
import com.ttn.tickets.persistence.RefreshIntentRepository;
import com.ttn.tickets.persistence.TicketRepository;

@Service
public class TicketCommandService {

    private final TicketRepository tickets;
    private final RefreshIntentRepository intents;
    private final Clock clock;

    public TicketCommandService(TicketRepository tickets, RefreshIntentRepository intents, Clock clock) {
        this.tickets = tickets;
        this.intents = intents;
        this.clock = clock;
    }

    @Transactional
    public TicketDetails create(String title, String description, String priority, String category, String assignee) {
        Instant now = now();
        Ticket ticket = Ticket.create(
                title,
                description,
                Priority.valueOf(priority),
                Category.valueOf(category),
                assignee,
                now);
        Ticket saved = tickets.saveAndFlush(ticket);
        intents.save(new RefreshIntent(saved.getId(), ChangeType.TICKET_CREATED, now));
        return TicketDetails.from(saved);
    }

    @Transactional
    public TicketDetails transition(long id, TicketStatus target, String notesOrReason) {
        Ticket ticket = tickets.findByIdForUpdate(id).orElseThrow(() -> new TicketNotFoundException(id));
        Instant now = now();
        ticket.moveTo(target, notesOrReason, now);
        intents.save(new RefreshIntent(ticket.getId(), ChangeType.STATUS_CHANGED, now));
        return TicketDetails.from(ticket);
    }

    @Transactional
    public TicketDetails edit(long id, Ticket.Changes changes) {
        Ticket ticket = tickets.findByIdForUpdate(id).orElseThrow(() -> new TicketNotFoundException(id));
        Instant now = now();
        ticket.edit(changes, now);
        intents.save(new RefreshIntent(ticket.getId(), ChangeType.FIELDS_UPDATED, now));
        return TicketDetails.from(ticket);
    }

    @Transactional
    public CommentView addComment(long id, String author, String text) {
        Ticket ticket = tickets.findByIdForUpdate(id).orElseThrow(() -> new TicketNotFoundException(id));
        Instant now = now();
        Comment comment = ticket.addComment(author, text, now);
        intents.save(new RefreshIntent(ticket.getId(), ChangeType.COMMENT_ADDED, now));
        return new CommentView(comment.getAuthor(), comment.getText(), comment.getCreatedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
