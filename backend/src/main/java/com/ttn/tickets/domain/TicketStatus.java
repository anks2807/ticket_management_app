package com.ttn.tickets.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public boolean canMoveTo(TicketStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    public Set<TicketStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(RESOLVED, CANCELLED);
            case RESOLVED -> EnumSet.of(CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(TicketStatus.class);
        };
    }
}
