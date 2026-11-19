package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
class SearchAndListIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SearchAndListIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void listsNewestFirstInPagesOfTwenty() throws Exception {
        for (int index = 0; index < 25; index++) {
            Instant updated = Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index);
            insertTicket("Ticket " + index, "Body " + index, "BILLING", null, "OPEN", null, null, updated, updated);
        }

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].title").value("Ticket 24"))
                .andExpect(jsonPath("$.items[19].title").value("Ticket 5"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].comments").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].priority").value("LOW"))
                .andExpect(jsonPath("$.items[0].category").value("BILLING"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].updatedAt").exists());

        mockMvc.perform(get("/api/tickets").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].title").value("Ticket 4"))
                .andExpect(jsonPath("$.items[4].title").value("Ticket 0"));

        mockMvc.perform(get("/api/tickets").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(25));
    }

    @Test
    void breaksTimestampTiesByIdentifierDescending() throws Exception {
        Instant same = Instant.parse("2024-06-01T00:00:00Z");
        long first = insertTicket("Older id", "Body", "BILLING", null, "OPEN", null, null, same, same);
        long second = insertTicket("Newer id", "Body", "BILLING", null, "OPEN", null, null, same, same);

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("TKT-" + second))
                .andExpect(jsonPath("$.items[1].id").value("TKT-" + first));
    }

    @Test
    void matchesEachSearchableFieldIgnoringCase() throws Exception {
        String titleId = createTicket("Printer jam on three", "Ordinary description", "BILLING", "nobody");
        String descriptionId = createTicket("Ordinary title", "The paper tray is empty", "BILLING", "nobody");
        String categoryId = createTicket("Ordinary title", "Ordinary description", "TECHNICAL", "nobody");
        String assigneeId = createTicket("Ordinary title", "Ordinary description", "BILLING", "Casey Agent");
        String notesId = resolvedTicket("Replaced the roller");
        String commentId = createTicket("Ordinary title", "Ordinary description", "BILLING", "nobody");
        comment(commentId, "Spoke with the customer");

        assertThat(idsFor("q", "PRINTER JAM")).containsExactly(titleId);
        assertThat(idsFor("q", "paper tray")).containsExactly(descriptionId);
        assertThat(idsFor("q", "technical")).containsExactly(categoryId);
        assertThat(idsFor("q", "CASEY AGENT")).containsExactly(assigneeId);
        assertThat(idsFor("q", "replaced the roller")).containsExactly(notesId);
        assertThat(idsFor("q", "SPOKE WITH")).containsExactly(commentId);
    }

    @Test
    void doesNotSearchTheCancellationReason() throws Exception {
        String id = createTicket("Ordinary title", "Ordinary description", "BILLING", null);
        move(id, "{\"status\":\"CANCELLED\",\"cancellationReason\":\"secret-cancel-token\"}");

        assertThat(idsFor("q", "secret-cancel-token")).isEmpty();
        assertThat(idsFor("q", "ordinary title")).containsExactly(id);
    }

    @Test
    void matchesTheCanonicalIdentifierIgnoringCaseAndSurroundingWhitespace() throws Exception {
        String id = createTicket("Plain title", "Plain body", "BILLING", null);

        assertThat(idsFor("q", "  " + id.toLowerCase() + "  ")).containsExactly(id);
        assertThat(idsFor("q", id.toUpperCase())).containsExactly(id);
    }

    @Test
    void doesNotMatchAPartialIdentifier() throws Exception {
        String id = createTicket("Plain title", "Plain body", "BILLING", null);
        String numeric = id.substring(4);

        assertThat(idsFor("q", numeric)).isEmpty();
        assertThat(idsFor("q", "TKT-0" + numeric)).isEmpty();
        assertThat(idsFor("q", id.substring(0, id.length() - 1))).isEmpty();
    }

    @Test
    void matchesPercentUnderscoreAndBackslashLiterally() throws Exception {
        String percent = createTicket("save 100% now", "Body", "BILLING", null);
        createTicket("save 1000 now", "Body", "BILLING", null);
        createTicket("save 100X now", "Body", "BILLING", null);
        String underscore = createTicket("file_name", "Body", "BILLING", null);
        createTicket("fileXname", "Body", "BILLING", null);
        String slash = createTicket("path\\end", "Body", "BILLING", null);
        createTicket("pathXend", "Body", "BILLING", null);

        assertThat(idsFor("q", "100%")).containsExactly(percent);
        assertThat(idsFor("q", "file_name")).containsExactly(underscore);
        assertThat(idsFor("q", "path\\end")).containsExactly(slash);
    }

    @Test
    void filtersByStatusAndCombinesItWithTheKeyword() throws Exception {
        String open = createTicket("shared-keyword open", "Body", "BILLING", null);
        String inProgress = createTicket("shared-keyword progress", "Body", "BILLING", null);
        move(inProgress, "{\"status\":\"IN_PROGRESS\"}");
        String other = createTicket("different wording", "Body", "BILLING", null);

        assertThat(idsFor("status", "OPEN")).contains(open, other).doesNotContain(inProgress);
        assertThat(idsFor("q", "shared-keyword", "status", "OPEN")).containsExactly(open);
        assertThat(idsFor("q", "shared-keyword", "status", "IN_PROGRESS")).containsExactly(inProgress);
    }

    @Test
    void returnsAnEmptyListWhenNothingMatches() throws Exception {
        createTicket("Plain title", "Plain body", "BILLING", null);

        mockMvc.perform(get("/api/tickets").param("q", "no-such-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        mockMvc.perform(get("/api/tickets").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void rejectsAnUnknownStatusByName() throws Exception {
        mockMvc.perform(get("/api/tickets").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("status")))
                .andExpect(jsonPath("$.errors[0].allowedValues",
                        containsInAnyOrder("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED")));
    }

    @Test
    void rejectsANegativeOrNonIntegerPage() throws Exception {
        mockMvc.perform(get("/api/tickets").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("page")));

        mockMvc.perform(get("/api/tickets").param("page", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("page")));
    }

    @Test
    void findsAKnownTicketAmongAtLeastOneThousand() throws Exception {
        jdbcTemplate.batchUpdate("""
                INSERT INTO ticket (
                    id, title, description, priority, category, assignee, status, created_at, updated_at)
                VALUES (nextval('ticket_id_seq'), ?, 'Bulk body', 'LOW', 'BILLING', NULL, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                statement.setString(1, index == 0 ? "needle-sc007" : "bulk-" + index);
                statement.setString(2, index == 0 ? "IN_PROGRESS" : "OPEN");
                Timestamp when = Timestamp.from(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index));
                statement.setTimestamp(3, when);
                statement.setTimestamp(4, when);
            }

            @Override
            public int getBatchSize() {
                return 1000;
            }
        });

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.totalItems").value(1000))
                .andExpect(jsonPath("$.totalPages").value(50));

        assertThat(idsFor("q", "needle-sc007", "status", "IN_PROGRESS")).hasSize(1);
        assertThat(idsFor("q", "NEEDLE-SC007")).hasSize(1);
        assertThat(idsFor("q", "needle-sc007", "status", "OPEN")).isEmpty();
    }

    private List<String> idsFor(String... params) throws Exception {
        var request = get("/api/tickets");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id");
    }

    private String createTicket(String title, String description, String category, String assignee) throws Exception {
        String assigneeJson = assignee == null ? "" : ",\"assignee\":" + quote(assignee);
        String body = "{\"title\":" + quote(title)
                + ",\"description\":" + quote(description)
                + ",\"priority\":\"LOW\",\"category\":" + quote(category)
                + assigneeJson + "}";
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String resolvedTicket(String notes) throws Exception {
        String id = createTicket("Notes ticket", "Body", "BILLING", null);
        move(id, "{\"status\":\"IN_PROGRESS\"}");
        move(id, "{\"status\":\"RESOLVED\",\"resolutionNotes\":\"" + notes + "\"}");
        return id;
    }

    private void move(String id, String body) throws Exception {
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void comment(String id, String text) throws Exception {
        mockMvc.perform(post("/api/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    private long insertTicket(String title, String description, String category, String assignee, String status,
            String notes, String reason, Instant createdAt, Instant updatedAt) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO ticket (
                    id, title, description, priority, category, assignee, status,
                    resolution_notes, cancellation_reason, created_at, updated_at)
                VALUES (nextval('ticket_id_seq'), ?, ?, 'LOW', ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, title, description, category, assignee, status, notes, reason,
                Timestamp.from(createdAt), Timestamp.from(updatedAt));
        if (id == null) {
            throw new IllegalStateException("insert did not return an id");
        }
        return id;
    }
}
