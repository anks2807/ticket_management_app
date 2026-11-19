package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
class CommentIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CommentIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void addsACommentInEveryStatusAndRecordsAnIntent() throws Exception {
        expectCommentAccepted(openTicket(), "OPEN");
        expectCommentAccepted(inProgressTicket(), "IN_PROGRESS");
        expectCommentAccepted(resolvedTicket(), "RESOLVED");
        expectCommentAccepted(closedTicket(), "CLOSED");
        expectCommentAccepted(cancelledTicket(), "CANCELLED");
    }

    @Test
    void showsCommentsOldestFirstAndLeavesTheEarlierOneUnchanged() throws Exception {
        String id = openTicket();
        addComment(id, "Ada Lovelace", "Checked the tray");
        CommentRow first = readComment(id, 1);
        int intents = intentCount(id);
        Thread.sleep(2);

        addComment(id, "Grace Hopper", "Reset the spool");

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(2))
                .andExpect(jsonPath("$.comments[0].author").value("Ada Lovelace"))
                .andExpect(jsonPath("$.comments[0].text").value("Checked the tray"))
                .andExpect(jsonPath("$.comments[1].author").value("Grace Hopper"))
                .andExpect(jsonPath("$.comments[1].text").value("Reset the spool"));

        assertThat(readComment(id, 1)).isEqualTo(first);
        assertThat(commentCount(id)).isEqualTo(2);
        assertThat(commentIntentCount(id)).isEqualTo(2);
        assertThat(intentCount(id)).isEqualTo(intents + 1);

        CommentRow beforeRefusal = readComment(id, 1);
        mockMvc.perform(put("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada Lovelace\",\"text\":\"Changed\"}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/tickets/" + id + "/comments"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(readComment(id, 1)).isEqualTo(beforeRefusal);
        assertThat(commentCount(id)).isEqualTo(2);
        assertThat(commentIntentCount(id)).isEqualTo(2);
    }

    @Test
    void rejectsABlankOrOversizedCommentAndRecordsNoIntent() throws Exception {
        String id = openTicket();
        OffsetDateTime updatedAt = updatedAt(id);
        int intents = intentCount(id);

        expectValidation(id, "{\"text\":\"Hello\"}", "author");
        expectValidation(id, "{\"author\":\"   \",\"text\":\"Hello\"}", "author");
        expectValidation(id, "{\"author\":\"Ada\",\"text\":\"  \"}", "text");
        mockMvc.perform(post("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"  \",\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("author", "text")));
        expectValidation(id, "{\"author\":\"%s\",\"text\":\"Hello\"}".formatted("a".repeat(101)), "author");
        expectValidation(id, "{\"author\":\"Ada\",\"text\":\"%s\"}".formatted("a".repeat(5001)), "text");
        mockMvc.perform(post("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada\",\"text\":\"Hello\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("status")));

        assertThat(commentCount(id)).isZero();
        assertThat(commentIntentCount(id)).isZero();
        assertThat(intentCount(id)).isEqualTo(intents);
        assertThat(updatedAt(id)).isEqualTo(updatedAt);
    }

    @Test
    void validatesTheBodyBeforeLookup() throws Exception {
        mockMvc.perform(post("/api/tickets/TKT-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"  \",\"text\":\"Hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("author")));

        mockMvc.perform(post("/api/tickets/TKT-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada\",\"text\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"));
        assertThat(commentIntentCount("TKT-1")).isZero();
    }

    private void expectCommentAccepted(String id, String status) throws Exception {
        OffsetDateTime before = updatedAt(id);
        int intents = intentCount(id);
        Thread.sleep(2);

        MvcResult created = mockMvc.perform(post("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada Lovelace\",\"text\":\"Checked the tray\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Ada Lovelace"))
                .andExpect(jsonPath("$.text").value("Checked the tray"))
                .andReturn();

        Instant commentTime = Instant.parse(JsonPath.read(created.getResponse().getContentAsString(), "$.createdAt"));
        OffsetDateTime after = updatedAt(id);
        assertThat(after).isAfter(before);
        assertThat(after.toInstant()).isEqualTo(commentTime);
        assertThat(readComment(id, 1).createdAt().toInstant()).isEqualTo(commentTime);
        assertThat(commentIntentCount(id)).isEqualTo(1);
        assertThat(intentCount(id)).isEqualTo(intents + 1);

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].author").value("Ada Lovelace"))
                .andExpect(jsonPath("$.comments[0].text").value("Checked the tray"));
    }

    private void expectValidation(String id, String body, String field) throws Exception {
        mockMvc.perform(post("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    private MvcResult addComment(String id, String author, String text) throws Exception {
        return mockMvc.perform(post("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"%s\",\"text\":\"%s\"}".formatted(author, text)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String openTicket() throws Exception {
        String response = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","description":"Body","priority":"LOW","category":"BILLING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String inProgressTicket() throws Exception {
        String id = openTicket();
        move(id, "{\"status\":\"IN_PROGRESS\"}");
        return id;
    }

    private String resolvedTicket() throws Exception {
        String id = inProgressTicket();
        move(id, "{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Fixed the jam\"}");
        return id;
    }

    private String closedTicket() throws Exception {
        String id = resolvedTicket();
        move(id, "{\"status\":\"CLOSED\"}");
        return id;
    }

    private String cancelledTicket() throws Exception {
        String id = openTicket();
        move(id, "{\"status\":\"CANCELLED\",\"cancellationReason\":\"Duplicate\"}");
        return id;
    }

    private void move(String id, String body) throws Exception {
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private OffsetDateTime updatedAt(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM ticket WHERE id = ?", OffsetDateTime.class, numericId(externalId));
    }

    private int commentCount(String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM comment WHERE ticket_id = ?", Integer.class, numericId(externalId));
        return count == null ? 0 : count;
    }

    private CommentRow readComment(String externalId, int position) {
        return jdbcTemplate.queryForObject("""
                SELECT author, body, created_at
                FROM comment
                WHERE ticket_id = ?
                ORDER BY id
                OFFSET ? LIMIT 1
                """, (rs, row) -> new CommentRow(
                rs.getString(1),
                rs.getString(2),
                rs.getObject(3, OffsetDateTime.class)), numericId(externalId), position - 1);
    }

    private int intentCount(String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_refresh_intent WHERE ticket_id = ?",
                Integer.class, numericId(externalId));
        return count == null ? 0 : count;
    }

    private int commentIntentCount(String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_refresh_intent WHERE ticket_id = ? AND change_type = 'COMMENT_ADDED'",
                Integer.class, numericId(externalId));
        return count == null ? 0 : count;
    }

    private static long numericId(String externalId) {
        return Long.parseLong(externalId.substring(4));
    }

    private record CommentRow(String author, String text, OffsetDateTime createdAt) {
    }
}
