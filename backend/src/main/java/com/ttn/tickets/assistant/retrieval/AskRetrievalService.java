package com.ttn.tickets.assistant.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.ttn.tickets.assistant.config.AssistantProperties;
import com.ttn.tickets.assistant.web.dto.TicketSourceDto;

@Service
public class AskRetrievalService {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("^Ticket TKT-[1-9][0-9]* \\| (.+?) \\| ");

    private final VectorStore vectorStore;
    private final AssistantProperties assistantProperties;
    private final TicketIdentifierParser ticketIdentifierParser;

    public AskRetrievalService(
            VectorStore vectorStore,
            AssistantProperties assistantProperties,
            TicketIdentifierParser ticketIdentifierParser) {
        this.vectorStore = vectorStore;
        this.assistantProperties = assistantProperties;
        this.ticketIdentifierParser = ticketIdentifierParser;
    }

    public RetrievalResult retrieve(String question) {
        var retrieval = assistantProperties.getRetrieval();
        List<Document> merged = new ArrayList<>();
        List<String> forcedIds = ticketIdentifierParser.parse(question);
        for (String ticketId : forcedIds) {
            var filter = new FilterExpressionBuilder().eq("ticketId", ticketId).build();
            SearchRequest forcedRequest = SearchRequest.builder()
                    .query(question)
                    .topK(retrieval.getTopK())
                    .similarityThresholdAll()
                    .filterExpression(filter)
                    .build();
            merged.addAll(vectorStore.similaritySearch(forcedRequest));
        }

        SearchRequest similarityRequest = SearchRequest.builder()
                .query(question)
                .topK(retrieval.getTopK())
                .similarityThreshold(retrieval.getSimilarityThreshold())
                .build();
        merged.addAll(vectorStore.similaritySearch(similarityRequest));

        Map<String, Document> bestByTicket = new LinkedHashMap<>();
        for (Document document : merged) {
            Object ticketId = document.getMetadata().get("ticketId");
            if (ticketId == null) {
                continue;
            }
            bestByTicket.putIfAbsent(ticketId.toString(), document);
        }

        List<Document> documents = new ArrayList<>(bestByTicket.values());
        List<TicketSourceDto> sources = documents.stream().map(this::toSource).toList();
        return new RetrievalResult(documents, sources);
    }

    private TicketSourceDto toSource(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String ticketId = stringValue(metadata.get("ticketId"));
        String status = stringValue(metadata.get("status"));
        String title = extractTitle(document.getText());
        return new TicketSourceDto(ticketId, title, status);
    }

    private static String extractTitle(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = TITLE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    public record RetrievalResult(List<Document> documents, List<TicketSourceDto> sources) {
    }
}