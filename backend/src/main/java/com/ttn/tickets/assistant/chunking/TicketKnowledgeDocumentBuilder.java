package com.ttn.tickets.assistant.chunking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.ttn.tickets.assistant.config.AssistantProperties;
import com.ttn.tickets.domain.Comment;
import com.ttn.tickets.domain.Ticket;

@Component
public class TicketKnowledgeDocumentBuilder {

    private final int maxChunkChars;

    public TicketKnowledgeDocumentBuilder(AssistantProperties assistantProperties) {
        this.maxChunkChars = assistantProperties.getChunking().getMaxTokens() * 4;
    }

    public List<Document> build(Ticket ticket) {
        String ticketId = Ticket.formatId(ticket.getId());
        String header = "Ticket %s | %s | %s | %s | %s"
                .formatted(ticketId, ticket.getTitle(), ticket.getCategory(), ticket.getPriority(), ticket.getStatus());
        List<String> sections = new ArrayList<>();
        sections.add(header);
        sections.add("Description\n" + ticket.getDescription());
        if (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank()) {
            sections.add("Resolution notes\n" + ticket.getResolutionNotes());
        }
        if (ticket.getCancellationReason() != null && !ticket.getCancellationReason().isBlank()) {
            sections.add("Cancellation reason\n" + ticket.getCancellationReason());
        }
        if (!ticket.getComments().isEmpty()) {
            StringBuilder comments = new StringBuilder("Comments");
            for (Comment comment : ticket.getComments()) {
                comments.append("\n").append(comment.getAuthor()).append(": ").append(comment.getText());
            }
            sections.add(comments.toString());
        }

        String fullDocument = String.join("\n\n", sections);
        List<String> chunks = chunkSections(sections, header);
        if (chunks.isEmpty()) {
            chunks = List.of(fullDocument);
        }

        Map<String, Object> baseMetadata = new LinkedHashMap<>();
        baseMetadata.put("ticketId", ticketId);
        baseMetadata.put("status", ticket.getStatus().name());
        baseMetadata.put("priority", ticket.getPriority().name());
        baseMetadata.put("assignee", ticket.getAssignee() == null ? "" : ticket.getAssignee());
        baseMetadata.put("category", ticket.getCategory().name());

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata);
            metadata.put("chunkType", chunks.size() == 1 ? "full" : (i == 0 ? "header" : "section"));
            documents.add(new Document(chunks.get(i), metadata));
        }
        return documents;
    }

    private List<String> chunkSections(List<String> sections, String header) {
        String combined = String.join("\n\n", sections);
        if (combined.length() <= maxChunkChars) {
            return List.of(combined);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder(header).append("\n\n");
        for (int i = 1; i < sections.size(); i++) {
            String section = sections.get(i);
            if (current.length() + section.length() + 2 > maxChunkChars && current.length() > header.length() + 2) {
                chunks.add(current.toString().trim());
                current = new StringBuilder(header).append("\n\n");
            }
            if (section.length() > maxChunkChars) {
                if (current.length() > header.length() + 2) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder(header).append("\n\n");
                }
                chunks.add(header + "\n\n" + section);
                continue;
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(section);
        }
        if (current.length() > header.length() + 2) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }
}