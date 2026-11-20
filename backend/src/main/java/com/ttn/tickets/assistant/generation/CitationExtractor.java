package com.ttn.tickets.assistant.generation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class CitationExtractor {

    private static final Pattern CITATION = Pattern.compile("\\[TKT-([1-9][0-9]*)\\]");

    public List<String> extract(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        Matcher matcher = CITATION.matcher(answer);
        Set<String> citations = new LinkedHashSet<>();
        while (matcher.find()) {
            citations.add("TKT-" + matcher.group(1));
        }
        return new ArrayList<>(citations);
    }
}