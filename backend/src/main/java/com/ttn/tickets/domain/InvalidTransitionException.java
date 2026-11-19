package com.ttn.tickets.domain;

import java.util.Set;

public class InvalidTransitionException extends RuntimeException {

    private final TicketStatus currentStatus;
    private final Set<TicketStatus> allowedTransitions;

    public InvalidTransitionException(TicketStatus currentStatus, Set<TicketStatus> allowedTransitions) {
        super("Transition from " + currentStatus + " is not allowed");
        this.currentStatus = currentStatus;
        this.allowedTransitions = Set.copyOf(allowedTransitions);
    }

    public TicketStatus currentStatus() {
        return currentStatus;
    }

    public Set<TicketStatus> allowedTransitions() {
        return allowedTransitions;
    }
}
