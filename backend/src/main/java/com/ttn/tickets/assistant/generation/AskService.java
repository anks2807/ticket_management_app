package com.ttn.tickets.assistant.generation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ttn.tickets.assistant.retrieval.AskRetrievalService;
import com.ttn.tickets.assistant.retrieval.QuestionValidator;
import com.ttn.tickets.assistant.web.dto.AskResponse;
import com.ttn.tickets.assistant.web.dto.TicketSourceDto;

@Service
public class AskService {

    public static final String OUTCOME_GROUNDED = "GROUNDED";
    public static final String OUTCOME_NO_RELEVANT_TICKETS = "NO_RELEVANT_TICKETS";
    public static final String OUTCOME_CITATION_VERIFICATION_FAILED = "CITATION_VERIFICATION_FAILED";

    private final QuestionValidator questionValidator;
    private final AskRetrievalService askRetrievalService;
    private final GroundedAnswerService groundedAnswerService;
    private final CitationExtractor citationExtractor;
    private final CitationVerifier citationVerifier;

    public AskService(
            QuestionValidator questionValidator,
            AskRetrievalService askRetrievalService,
            GroundedAnswerService groundedAnswerService,
            CitationExtractor citationExtractor,
            CitationVerifier citationVerifier) {
        this.questionValidator = questionValidator;
        this.askRetrievalService = askRetrievalService;
        this.groundedAnswerService = groundedAnswerService;
        this.citationExtractor = citationExtractor;
        this.citationVerifier = citationVerifier;
    }

    public AskResponse ask(String question) {
        String validated = questionValidator.validate(question);
        AskRetrievalService.RetrievalResult retrieval = askRetrievalService.retrieve(validated);
        if (retrieval.documents().isEmpty()) {
            return new AskResponse(OUTCOME_NO_RELEVANT_TICKETS, null, null);
        }

        String answer = groundedAnswerService.generate(validated, retrieval.documents());
        List<String> citations = citationExtractor.extract(answer);
        Set<String> retrievedIds = new HashSet<>();
        for (TicketSourceDto source : retrieval.sources()) {
            retrievedIds.add(source.ticketId());
        }
        if (!citationVerifier.allCitationsInRetrievedSet(citations, retrievedIds)) {
            return new AskResponse(OUTCOME_CITATION_VERIFICATION_FAILED, null, null);
        }
        return new AskResponse(OUTCOME_GROUNDED, answer, retrieval.sources());
    }
}