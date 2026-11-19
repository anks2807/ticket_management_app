package com.ttn.tickets.domain;

public class ResolutionNotesNotEditableException extends RuntimeException {

    private final TicketStatus currentStatus;

    public ResolutionNotesNotEditableException(TicketStatus currentStatus) {
        super("Resolution notes cannot be edited while the ticket is " + currentStatus);
        this.currentStatus = currentStatus;
    }

    public TicketStatus currentStatus() {
        return currentStatus;
    }
}
