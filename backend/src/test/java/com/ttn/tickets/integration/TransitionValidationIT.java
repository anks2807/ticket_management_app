package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
class TransitionValidationIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TransitionValidationIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void requiresResolutionNotesWhenResolving() throws Exception {
        String id = inProgressTicket();
        expectValidation(id, "{\"status\":\"RESOLVED\"}", "resolutionNotes");
        expectValidation(id, "{\"status\":\"RESOLVED\",\"resolutionNotes\":\"   \"}", "resolutionNotes");
        assertUnchanged(id, "IN_PROGRESS");
    }

    @Test
    void requiresACancellationReasonWhenCancelling() throws Exception {
        String id = openTicket();
        expectValidation(id, "{\"status\":\"CANCELLED\"}", "cancellationReason");
        expectValidation(id, "{\"status\":\"CANCELLED\",\"cancellationReason\":\"\"}", "cancellationReason");
        assertUnchanged(id, "OPEN");
    }

    @Test
    void rejectsFieldsThatDoNotBelongToTheTargetStatus() throws Exception {
        String open = openTicket();
        expectValidation(open, "{\"status\":\"IN_PROGRESS\",\"resolutionNotes\":\"not for this move\"}", "resolutionNotes");
        expectValidation(open, "{\"status\":\"IN_PROGRESS\",\"cancellationReason\":\"not for this move\"}", "cancellationReason");
        assertUnchanged(open, "OPEN");

        String inProgress = inProgressTicket();
        expectValidation(inProgress,
                "{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Fixed\",\"cancellationReason\":\"not for this move\"}",
                "cancellationReason");
        expectValidation(inProgress,
                "{\"status\":\"CANCELLED\",\"cancellationReason\":\"Duplicate\",\"resolutionNotes\":\"not for this move\"}",
                "resolutionNotes");
        assertUnchanged(inProgress, "IN_PROGRESS");
    }

    @Test
    void unknownStatusIsValidationFailedAndLeavesTheTicketUnchanged() throws Exception {
        String id = openTicket();
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("status")));
        assertUnchanged(id, "OPEN");
    }

    @Test
    void unknownFieldIsMalformedAndLeavesTheTicketUnchanged() throws Exception {
        String id = openTicket();
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"extra\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("extra")));
        assertUnchanged(id, "OPEN");
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(post("/api/tickets/TKT-1/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"));
    }

    @Test
    void validationRunsBeforeLookup() throws Exception {
        mockMvc.perform(post("/api/tickets/TKT-1/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("resolutionNotes")));
    }

    private void expectValidation(String id, String body, String field) throws Exception {
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
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
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private void assertUnchanged(String externalId, String status) {
        long id = Long.parseLong(externalId.substring(4));
        String stored = jdbcTemplate.queryForObject("SELECT status FROM ticket WHERE id = ?", String.class, id);
        OffsetDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM ticket WHERE id = ?", OffsetDateTime.class, id);
        OffsetDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM ticket WHERE id = ?", OffsetDateTime.class, id);
        assertThat(stored).isEqualTo(status);
        if ("OPEN".equals(status)) {
            assertThat(updatedAt).isEqualTo(createdAt);
        }
    }
}
