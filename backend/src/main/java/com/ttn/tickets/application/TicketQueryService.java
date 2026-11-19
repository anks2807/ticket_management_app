package com.ttn.tickets.application;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttn.tickets.application.view.TicketDetails;
import com.ttn.tickets.application.view.TicketPage;
import com.ttn.tickets.application.view.TicketSummary;
import com.ttn.tickets.domain.TicketStatus;
import com.ttn.tickets.persistence.TicketRepository;
import com.ttn.tickets.persistence.TicketSearchSpecifications;

@Service
public class TicketQueryService {

    private final TicketRepository tickets;

    public TicketQueryService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional(readOnly = true)
    public TicketDetails getById(long id) {
        return tickets.findById(id)
                .map(TicketDetails::from)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public TicketPage list(int page) {
        return search(null, null, page);
    }

    @Transactional(readOnly = true)
    public TicketPage search(String keyword, TicketStatus status, int page) {
        var result = tickets.findAll(
                TicketSearchSpecifications.matching(keyword, status),
                PageRequest.of(page, TicketPage.PAGE_SIZE,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        List<TicketSummary> items = result.getContent().stream().map(TicketSummary::from).toList();
        return new TicketPage(items, page, TicketPage.PAGE_SIZE, result.getTotalElements(), result.getTotalPages());
    }
}
