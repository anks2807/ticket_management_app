package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.ttn.tickets.domain.ChangeType;
import com.ttn.tickets.domain.TicketStatus;

@AutoConfigureMockMvc
class RefreshIntentIT extends PostgresIntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RefreshIntentIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void dropTestTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS ttn_test_fail_intent ON ticket_refresh_intent");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS ttn_test_fail_ticket ON ticket");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS ttn_test_fail_intent()");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS ttn_test_fail_ticket()");
    }

    @Test
    void recordsIntentOnEveryCommittedChange() throws Exception {
        assertThat(totalIntents()).isZero();

        String publicId = createTicket();
        long ticketId = numericId(publicId);
        assertThat(intents(ticketId)).containsExactly(ChangeType.TICKET_CREATED.name());

        mockMvc.perform(patch("/api/tickets/" + publicId)
                        .contentType(MERGE_PATCH)
                        .content("{\"description\":\"After create\"}"))
                .andExpect(status().isOk());
        assertThat(intents(ticketId)).contains(ChangeType.TICKET_CREATED.name(), ChangeType.FIELDS_UPDATED.name());

        mockMvc.perform(post("/api/tickets/" + publicId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"agent@example.com\",\"text\":\"Customer called\"}"))
                .andExpect(status().isCreated());
        assertThat(intents(ticketId)).contains(ChangeType.COMMENT_ADDED.name());

        for (TicketStatus target : new TicketStatus[] {
                TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.CANCELLED }) {
            jdbcTemplate.execute("TRUNCATE TABLE ticket_refresh_intent, comment, ticket");
            publicId = ticketReadyFor(target);
            ticketId = numericId(publicId);
            int before = intentCount(ticketId);
            moveTo(publicId, target);
            assertThat(intentCount(ticketId)).isEqualTo(before + 1);
            assertThat(latestIntent(ticketId)).isEqualTo(ChangeType.STATUS_CHANGED.name());
        }
    }

    @Test
    void refusedTransitionDoesNotRecordIntent() throws Exception {
        String publicId = createTicket();
        long ticketId = numericId(publicId);
        int before = intentCount(ticketId);
        mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
        assertThat(intentCount(ticketId)).isEqualTo(before);
    }

    @Test
    void intentInsertFailureRollsBackTicketChange() throws Exception {
        installIntentFailureTrigger();
        int ticketsBefore = ticketRows();
        int intentsBefore = totalIntents();

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Rollback","description":"Should not persist","priority":"LOW","category":"BILLING"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

        assertThat(ticketRows()).isEqualTo(ticketsBefore);
        assertThat(totalIntents()).isEqualTo(intentsBefore);
    }

    @Test
    void ticketCommitFailureRollsBackIntent() throws Exception {
        installDeferredTicketFailureTrigger();
        int ticketsBefore = ticketRows();
        int intentsBefore = totalIntents();

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Rollback ticket","description":"Should not persist","priority":"LOW","category":"BILLING"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

        assertThat(ticketRows()).isEqualTo(ticketsBefore);
        assertThat(totalIntents()).isEqualTo(intentsBefore);
    }

    private String createTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Intent case","description":"Intent coverage","priority":"MEDIUM","category":"TECHNICAL"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private String ticketReadyFor(TicketStatus target) throws Exception {
        String publicId = createTicket();
        if (target == TicketStatus.IN_PROGRESS || target == TicketStatus.CANCELLED) {
            return publicId;
        }
        mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        if (target == TicketStatus.RESOLVED) {
            return publicId;
        }
        mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Ready to close\"}"))
                .andExpect(status().isOk());
        return publicId;
    }

    private void moveTo(String publicId, TicketStatus target) throws Exception {
        String body = switch (target) {
            case IN_PROGRESS -> "{\"status\":\"IN_PROGRESS\"}";
            case RESOLVED -> "{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Done\"}";
            case CLOSED -> "{\"status\":\"CLOSED\"}";
            case CANCELLED -> "{\"status\":\"CANCELLED\",\"cancellationReason\":\"No longer needed\"}";
            default -> throw new IllegalArgumentException("unsupported target " + target);
        };
        mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void installIntentFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION ttn_test_fail_intent()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  RAISE EXCEPTION 'forced intent failure';
                END;
                $$;
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER ttn_test_fail_intent
                BEFORE INSERT ON ticket_refresh_intent
                FOR EACH ROW EXECUTE FUNCTION ttn_test_fail_intent();
                """);
    }

    private void installDeferredTicketFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION ttn_test_fail_ticket()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  RAISE EXCEPTION 'forced ticket failure';
                END;
                $$;
                """);
        jdbcTemplate.execute("""
                CREATE CONSTRAINT TRIGGER ttn_test_fail_ticket
                AFTER INSERT OR UPDATE ON ticket
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION ttn_test_fail_ticket();
                """);
    }

    private Set<String> intents(long ticketId) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT change_type FROM ticket_refresh_intent WHERE ticket_id = ? ORDER BY id",
                String.class,
                ticketId));
    }

    private String latestIntent(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT change_type FROM ticket_refresh_intent WHERE ticket_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                ticketId);
    }

    private int intentCount(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE ticket_id = ?", Integer.class, ticketId);
        return count == null ? 0 : count;
    }

    private int totalIntents() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_refresh_intent", Integer.class);
        return count == null ? 0 : count;
    }

    private int ticketRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket", Integer.class);
        return count == null ? 0 : count;
    }

    private long numericId(String publicId) {
        return Long.parseLong(publicId.substring("TKT-".length()));
    }
}