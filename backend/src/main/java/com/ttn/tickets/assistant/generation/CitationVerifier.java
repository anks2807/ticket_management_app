package com.ttn.tickets.assistant.generation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class CitationVerifier {

    public boolean allCitationsInRetrievedSet(List<String> citations, Set<String> retrievedTicketIds) {
        if (citations.isEmpty()) {
            return false;
        }
        Set<String> allowed = retrievedTicketIds == null ? Set.of() : new HashSet<>(retrievedTicketIds);
        for (String citation : citations) {
            if (!allowed.contains(citation)) {
                return false;
            }
        }
        return true;
    }
}