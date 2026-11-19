package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
class EditTicketIT extends PostgresIntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EditTicketIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void editsOnlyTheSuppliedFieldsAndRecordsAnIntent() throws Exception {
        String id = openTicket("Assigned");
        insertComment(id, "Ada Lovelace", "Checked the tray");
        TicketRow before = read(id);
        int intents = intentCount(id);
        Thread.sleep(2);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("""
                                {"title":"Paper jam","description":"Tray 2 is empty","priority":"URGENT","category":"SHIPPING","assignee":"casey"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Paper jam"))
                .andExpect(jsonPath("$.description").value("Tray 2 is empty"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.category").value("SHIPPING"))
                .andExpect(jsonPath("$.assignee").value("casey"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolutionNotes").value(nullValue()))
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].author").value("Ada Lovelace"))
                .andExpect(jsonPath("$.comments[0].text").value("Checked the tray"));

        TicketRow after = read(id);
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.commentCount()).isEqualTo(1);
        assertThat(fieldsUpdatedCount(id)).isEqualTo(1);
        assertThat(intentCount(id)).isEqualTo(intents + 1);

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.comments.length()").value(1));
    }

    @Test
    void leavesAbsentFieldsUnchanged() throws Exception {
        String id = openTicket("Assigned");
        int intents = intentCount(id);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Only title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Only title"))
                .andExpect(jsonPath("$.description").value("Body"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.category").value("BILLING"))
                .andExpect(jsonPath("$.assignee").value("Assigned"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(intentCount(id)).isEqualTo(intents + 1);
        assertThat(fieldsUpdatedCount(id)).isEqualTo(1);
    }

    @Test
    void clearsAssigneeOnlyWhenItIsExplicitlyNull() throws Exception {
        String id = openTicket("Assigned");

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"assignee\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value(nullValue()))
                .andExpect(jsonPath("$.title").value("Title"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(fieldsUpdatedCount(id)).isEqualTo(1);
    }

    @Test
    void blankAssigneeDoesNotClearTheAssignee() throws Exception {
        String id = openTicket("Assigned");
        TicketRow before = read(id);
        Thread.sleep(2);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"assignee\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("Assigned"));

        TicketRow after = read(id);
        assertThat(after.assignee()).isEqualTo("Assigned");
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(fieldsUpdatedCount(id)).isEqualTo(1);
    }

    @Test
    void rejectsNullOnFieldsThatCannotBeCleared() throws Exception {
        String id = openTicket("Assigned");
        TicketRow before = read(id);
        int intents = intentCount(id);

        expectValidation(id, "{\"title\":null}", "title");
        expectValidation(id, "{\"description\":null}", "description");
        expectValidation(id, "{\"priority\":null}", "priority");
        expectValidation(id, "{\"category\":null}", "category");

        assertThat(read(id)).isEqualTo(before);
        assertThat(intentCount(id)).isEqualTo(intents);
        assertThat(fieldsUpdatedCount(id)).isZero();
    }

    @Test
    void rejectsAnEmptyBody() throws Exception {
        String id = openTicket("Assigned");
        TicketRow before = read(id);
        int intents = intentCount(id);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(read(id)).isEqualTo(before);
        assertThat(intentCount(id)).isEqualTo(intents);
    }

    @Test
    void reportsEveryInvalidFieldAndListsAllowedCategoryValues() throws Exception {
        String id = openTicket("Assigned");
        TicketRow before = read(id);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"  \",\"priority\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "priority")));

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"category\":\"Misc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("category"))
                .andExpect(jsonPath("$.errors[0].allowedValues",
                        containsInAnyOrder("BILLING", "TECHNICAL", "ACCOUNT", "SHIPPING", "OTHER")));

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"%s\"}".formatted("a".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")));

        assertThat(read(id)).isEqualTo(before);
        assertThat(fieldsUpdatedCount(id)).isZero();
    }

    @Test
    void rejectsStatusIdentifierAndCreationTimeAsUnknownFields() throws Exception {
        String id = openTicket("Assigned");
        TicketRow before = read(id);
        int intents = intentCount(id);

        expectMalformed(id, "{\"status\":\"CLOSED\"}", "status");
        expectMalformed(id, "{\"id\":\"TKT-1\"}", "id");
        expectMalformed(id, "{\"createdAt\":\"2020-01-01T00:00:00Z\"}", "createdAt");
        expectMalformed(id, "{\"comments\":[]}", "comments");

        assertThat(read(id)).isEqualTo(before);
        assertThat(intentCount(id)).isEqualTo(intents);
    }

    @Test
    void refusesEditsOnClosedAndCancelledTickets() throws Exception {
        String closed = closedTicket();
        TicketRow closedBefore = read(closed);
        int closedIntents = intentCount(closed);
        expectFrozen(closed, "{\"title\":\"Changed\"}");
        expectFrozen(closed, "{\"resolutionNotes\":\"Changed notes\"}");
        assertThat(read(closed)).isEqualTo(closedBefore);
        assertThat(intentCount(closed)).isEqualTo(closedIntents);

        String cancelled = cancelledTicket();
        TicketRow cancelledBefore = read(cancelled);
        int cancelledIntents = intentCount(cancelled);
        expectFrozen(cancelled, "{\"assignee\":null}");
        expectFrozen(cancelled, "{\"category\":\"TECHNICAL\"}");
        expectFrozen(cancelled, "{\"resolutionNotes\":\"Should stay frozen\"}");
        assertThat(read(cancelled)).isEqualTo(cancelledBefore);
        assertThat(intentCount(cancelled)).isEqualTo(cancelledIntents);
        assertThat(fieldsUpdatedCount(closed)).isZero();
        assertThat(fieldsUpdatedCount(cancelled)).isZero();
    }

    @Test
    void refusesResolutionNotesUntilTheTicketHasSome() throws Exception {
        String id = openTicket(null);
        TicketRow before = read(id);
        int intents = intentCount(id);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"resolutionNotes\":\"Not yet\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("RESOLUTION_NOTES_NOT_EDITABLE"))
                .andExpect(jsonPath("$.currentStatus").value("OPEN"));

        assertThat(read(id)).isEqualTo(before);
        assertThat(intentCount(id)).isEqualTo(intents);
    }

    @Test
    void replacesResolutionNotesAndRejectsClearingThem() throws Exception {
        String id = resolvedTicket();

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content("{\"resolutionNotes\":\"Replaced notes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionNotes").value("Replaced notes"))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(fieldsUpdatedCount(id)).isEqualTo(1);

        TicketRow before = read(id);
        int intents = intentCount(id);
        expectValidation(id, "{\"resolutionNotes\":\"   \"}", "resolutionNotes");
        expectValidation(id, "{\"resolutionNotes\":null}", "resolutionNotes");
        assertThat(read(id)).isEqualTo(before);
        assertThat(intentCount(id)).isEqualTo(intents);
    }

    @Test
    void validatesTheBodyBeforeLookupOrState() throws Exception {
        mockMvc.perform(patch("/api/tickets/TKT-1")
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")));

        mockMvc.perform(patch("/api/tickets/TKT-1")
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Still here\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"));

        String closed = closedTicket();
        TicketRow before = read(closed);
        expectValidation(closed, "{\"title\":\"  \"}", "title");
        assertThat(read(closed)).isEqualTo(before);
        assertThat(fieldsUpdatedCount(closed)).isZero();
    }

    private void expectValidation(String id, String body, String field) throws Exception {
        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    private void expectMalformed(String id, String body, String field) throws Exception {
        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    private void expectFrozen(String id, String body) throws Exception {
        String status = read(id).status();
        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MERGE_PATCH)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("TICKET_FROZEN"))
                .andExpect(jsonPath("$.currentStatus").value(status));
    }

    private String openTicket(String assignee) throws Exception {
        String assigneeJson = assignee == null ? "" : ",\"assignee\":\"" + assignee + "\"";
        String response = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","description":"Body","priority":"LOW","category":"BILLING"%s}
                                """.formatted(assigneeJson)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String resolvedTicket() throws Exception {
        String id = openTicket(null);
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Fixed the jam\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String closedTicket() throws Exception {
        String id = resolvedTicket();
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String cancelledTicket() throws Exception {
        String id = openTicket("Assigned");
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"cancellationReason\":\"Duplicate\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private void insertComment(String externalId, String author, String text) {
        jdbcTemplate.update(
                "INSERT INTO comment (ticket_id, author, body, created_at) VALUES (?, ?, ?, now())",
                numericId(externalId), author, text);
    }

    private TicketRow read(String externalId) {
        return jdbcTemplate.queryForObject("""
                SELECT title, description, priority, category, assignee, status, resolution_notes,
                       cancellation_reason, created_at, updated_at,
                       (SELECT count(*) FROM comment WHERE ticket_id = ticket.id)
                FROM ticket WHERE id = ?
                """, (rs, row) -> new TicketRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                rs.getObject(9, OffsetDateTime.class),
                rs.getObject(10, OffsetDateTime.class),
                rs.getInt(11)), numericId(externalId));
    }

    private int intentCount(String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_refresh_intent WHERE ticket_id = ?",
                Integer.class, numericId(externalId));
        return count == null ? 0 : count;
    }

    private int fieldsUpdatedCount(String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_refresh_intent WHERE ticket_id = ? AND change_type = 'FIELDS_UPDATED'",
                Integer.class, numericId(externalId));
        return count == null ? 0 : count;
    }

    private static long numericId(String externalId) {
        return Long.parseLong(externalId.substring(4));
    }

    private record TicketRow(
            String title,
            String description,
            String priority,
            String category,
            String assignee,
            String status,
            String resolutionNotes,
            String cancellationReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int commentCount) {
    }
}
