package com.ttn.tickets.application;

import com.ttn.tickets.domain.Ticket;

public class TicketNotFoundException extends RuntimeException {

    private final String ticketId;

    public TicketNotFoundException(String ticketId) {
        super("Ticket " + ticketId + " was not found");
        this.ticketId = ticketId;
    }

    public TicketNotFoundException(long id) {
        this(Ticket.formatId(id));
    }

    public String ticketId() {
        return ticketId;
    }
}
