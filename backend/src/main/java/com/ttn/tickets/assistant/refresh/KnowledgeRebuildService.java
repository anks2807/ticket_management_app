package com.ttn.tickets.assistant.refresh;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttn.tickets.assistant.config.AssistantProperties;
import com.ttn.tickets.domain.ChangeType;
import com.ttn.tickets.domain.RefreshIntent;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.persistence.RefreshIntentRepository;
import com.ttn.tickets.persistence.TicketRepository;

@Service
public class KnowledgeRebuildService {

    private final TicketRepository ticketRepository;
    private final RefreshIntentRepository refreshIntentRepository;
    private final AssistantProperties assistantProperties;
    private final Clock clock;

    public KnowledgeRebuildService(
            TicketRepository ticketRepository,
            RefreshIntentRepository refreshIntentRepository,
            AssistantProperties assistantProperties,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.refreshIntentRepository = refreshIntentRepository;
        this.assistantProperties = assistantProperties;
        this.clock = clock;
    }

    @Transactional
    public int enqueueRebuildForAllTickets() {
        Instant now = clock.instant();
        int pageSize = assistantProperties.getRefresh().getBatchSize();
        List<RefreshIntent> intents = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<Ticket> page;
        do {
            page = ticketRepository.findAll(pageable);
            for (Ticket ticket : page.getContent()) {
                intents.add(new RefreshIntent(ticket.getId(), ChangeType.FIELDS_UPDATED, now));
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
        refreshIntentRepository.saveAll(intents);
        return intents.size();
    }
}