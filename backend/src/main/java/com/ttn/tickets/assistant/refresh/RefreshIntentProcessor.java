package com.ttn.tickets.assistant.refresh;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ttn.tickets.assistant.chunking.TicketKnowledgeDocumentBuilder;
import com.ttn.tickets.assistant.config.AssistantProperties;
import com.ttn.tickets.domain.RefreshIntent;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.persistence.RefreshIntentRepository;
import com.ttn.tickets.persistence.TicketRepository;

@Service
public class RefreshIntentProcessor {

    private static final Logger log = LoggerFactory.getLogger(RefreshIntentProcessor.class);

    private final RefreshIntentRepository refreshIntentRepository;
    private final TicketRepository ticketRepository;
    private final TicketKnowledgeDocumentBuilder documentBuilder;
    private final VectorStore vectorStore;
    private final AssistantProperties assistantProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RefreshIntentProcessor(
            RefreshIntentRepository refreshIntentRepository,
            TicketRepository ticketRepository,
            TicketKnowledgeDocumentBuilder documentBuilder,
            VectorStore vectorStore,
            AssistantProperties assistantProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.refreshIntentRepository = refreshIntentRepository;
        this.ticketRepository = ticketRepository;
        this.documentBuilder = documentBuilder;
        this.vectorStore = vectorStore;
        this.assistantProperties = assistantProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${assistant.refresh.poll-interval:5s}")
    public void scheduledProcess() {
        processOnce();
    }

    public void processOnce() {
        var now = clock.instant();
        int batchSize = assistantProperties.getRefresh().getBatchSize();
        List<RefreshIntent> claimed =
                transactionTemplate.execute(status -> refreshIntentRepository.claimPending(now, batchSize));
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        Map<Long, List<RefreshIntent>> byTicket = new LinkedHashMap<>();
        for (RefreshIntent intent : claimed) {
            byTicket.computeIfAbsent(intent.getTicketId(), ignored -> new ArrayList<>()).add(intent);
        }
        for (Map.Entry<Long, List<RefreshIntent>> entry : byTicket.entrySet()) {
            transactionTemplate.executeWithoutResult(status -> refreshTicket(entry.getKey(), entry.getValue(), now));
        }
    }

    private void refreshTicket(Long ticketId, List<RefreshIntent> intents, java.time.Instant now) {
        try {
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalStateException("Ticket not found: " + ticketId));
            String publicId = Ticket.formatId(ticketId);
            vectorStore.delete(new FilterExpressionBuilder().eq("ticketId", publicId).build());
            List<Document> documents = documentBuilder.build(ticket);
            vectorStore.add(documents);
            for (RefreshIntent intent : intents) {
                intent.markDone(now);
            }
            refreshIntentRepository.saveAll(intents);
        } catch (RuntimeException failure) {
            List<Long> intentIds = intents.stream().map(RefreshIntent::getId).toList();
            log.warn(
                    "Refresh indexing failed for ticketId={} publicId={} intentIds={}: {}",
                    ticketId,
                    Ticket.formatId(ticketId),
                    intentIds,
                    failure.getMessage());
            var refresh = assistantProperties.getRefresh();
            for (RefreshIntent intent : intents) {
                intent.recordFailure(failure.getMessage(), now, refresh.getMaxAttempts(), refresh.getBackoffBase());
            }
            refreshIntentRepository.saveAll(intents);
        }
    }
}