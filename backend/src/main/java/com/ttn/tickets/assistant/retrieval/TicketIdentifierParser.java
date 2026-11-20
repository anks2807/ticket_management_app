package com.ttn.tickets.assistant.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ttn.tickets.domain.Ticket;

@Component
public class TicketIdentifierParser {

    private static final Pattern IDENTIFIER = Pattern.compile("(?i)\\bTKT-([1-9][0-9]{0,17})\\b");

    public List<String> parse(String question) {
        Matcher matcher = IDENTIFIER.matcher(question);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            long numeric = Long.parseLong(matcher.group(1));
            ids.add(Ticket.formatId(numeric));
        }
        return new ArrayList<>(ids);
    }
}