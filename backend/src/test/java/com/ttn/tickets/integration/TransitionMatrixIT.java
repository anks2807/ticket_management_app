package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ttn.tickets.domain.TicketStatus;

@AutoConfigureMockMvc
class TransitionMatrixIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TransitionMatrixIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void everyFromToPair(TicketStatus from, TicketStatus to) throws Exception {
        long ticketId = insert(from);
        OffsetDateTime updatedAt = updatedAt(ticketId);
        int intentsBefore = intentCount(ticketId);

        var result = mockMvc.perform(post("/api/tickets/TKT-" + ticketId + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(to)));

        if (isAllowed(from, to)) {
            result.andExpect(status().isOk());
        } else {
            String[] allowed = expectedAllowed(from).stream().map(TicketStatus::name).toArray(String[]::new);
            result.andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"))
                    .andExpect(jsonPath("$.currentStatus").value(from.name()));
            if (allowed.length == 0) {
                result.andExpect(jsonPath("$.allowedTransitions").isEmpty());
            } else {
                result.andExpect(jsonPath("$.allowedTransitions", containsInAnyOrder(allowed)));
            }
            assertThat(updatedAt(ticketId)).isEqualTo(updatedAt);
            assertThat(statusOf(ticketId)).isEqualTo(from.name());
            assertThat(intentCount(ticketId)).isEqualTo(intentsBefore);
        }
    }

    private static Stream<Arguments> pairs() {
        return Stream.of(TicketStatus.values())
                .flatMap(from -> Stream.of(TicketStatus.values()).map(to -> Arguments.of(from, to)));
    }

    private long insert(TicketStatus status) {
        Long id = jdbcTemplate.queryForObject("SELECT nextval('ticket_id_seq')", Long.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String resolutionNotes = (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED)
                ? "Resolved in the fixture"
                : null;
        String cancellationReason = status == TicketStatus.CANCELLED ? "Cancelled in the fixture" : null;
        jdbcTemplate.update("""
                INSERT INTO ticket (
                    id, title, description, priority, category, status,
                    resolution_notes, cancellation_reason, created_at, updated_at)
                VALUES (?, 'Fixture title', 'Fixture description', 'LOW', 'BILLING', ?, ?, ?, ?, ?)
                """, id, status.name(), resolutionNotes, cancellationReason, now, now);
        return id;
    }

    private String body(TicketStatus target) {
        return switch (target) {
            case RESOLVED -> "{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Notes for the move\"}";
            case CANCELLED -> "{\"status\":\"CANCELLED\",\"cancellationReason\":\"Reason for the move\"}";
            default -> "{\"status\":\"" + target.name() + "\"}";
        };
    }

    private OffsetDateTime updatedAt(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM ticket WHERE id = ?", OffsetDateTime.class, ticketId);
    }

    private String statusOf(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ticket WHERE id = ?", String.class, ticketId);
    }

    private int intentCount(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE ticket_id = ?", Integer.class, ticketId);
        return count == null ? 0 : count;
    }

    private static Set<TicketStatus> expectedAllowed(TicketStatus status) {
        return switch (status) {
            case OPEN -> EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED);
            case IN_PROGRESS -> EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED);
            case RESOLVED -> EnumSet.of(TicketStatus.CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    private static boolean isAllowed(TicketStatus from, TicketStatus to) {
        return expectedAllowed(from).contains(to);
    }
}
