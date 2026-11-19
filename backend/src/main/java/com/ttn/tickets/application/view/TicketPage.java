package com.ttn.tickets.application.view;

import java.util.List;

public record TicketPage(List<TicketSummary> items, int page, int pageSize, long totalItems, int totalPages) {

    public static final int PAGE_SIZE = 20;

    public TicketPage {
        items = List.copyOf(items);
    }
}
