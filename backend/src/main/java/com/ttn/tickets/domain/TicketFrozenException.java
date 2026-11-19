package com.ttn.tickets.domain;

public class TicketFrozenException extends RuntimeException {

    private final TicketStatus currentStatus;

    public TicketFrozenException(TicketStatus currentStatus) {
        super("Ticket is frozen in status " + currentStatus);
        this.currentStatus = currentStatus;
    }

    public TicketStatus currentStatus() {
        return currentStatus;
    }
}
