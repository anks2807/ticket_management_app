package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CreateAndViewIT extends PostgresIntegrationTest {

    private static final String VALID = """
            {"title":"Printer jam","description":"The third-floor printer is jammed","priority":"HIGH","category":"TECHNICAL","assignee":"agent@example.com"}
            """;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CreateAndViewIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void createsWithEveryFieldAndReadsItBack() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/tickets/TKT-[1-9][0-9]*")))
                .andExpect(jsonPath("$.id", matchesPattern("TKT-[1-9][0-9]*")))
                .andExpect(jsonPath("$.title").value("Printer jam"))
                .andExpect(jsonPath("$.description").value("The third-floor printer is jammed"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.category").value("TECHNICAL"))
                .andExpect(jsonPath("$.assignee").value("agent@example.com"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolutionNotes").value(nullValue()))
                .andExpect(jsonPath("$.cancellationReason").value(nullValue()))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andReturn();

        String body = created.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.createdAt")).isEqualTo(JsonPath.read(body, "$.updatedAt"));

        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Printer jam"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andExpect(jsonPath("$.resolutionNotes").value(nullValue()))
                .andExpect(jsonPath("$.cancellationReason").value(nullValue()));
    }

    @Test
    void createsWithoutAssignee() throws Exception {
        String body = """
                {"title":"Invoice question","description":"March invoice looks wrong","priority":"LOW","category":"BILLING"}
                """;

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", matchesPattern("TKT-[1-9][0-9]*")))
                .andExpect(jsonPath("$.assignee").value(nullValue()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andExpect(jsonPath("$.resolutionNotes").value(nullValue()));
    }

    @Test
    void storesABlankAssigneeAsNoAssignee() throws Exception {
        String body = """
                {"title":"Invoice question","description":"March invoice looks wrong","priority":"LOW","category":"BILLING","assignee":"  "}
                """;

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee").value(nullValue()));
    }

    @Test
    void rejectsEachInvalidFieldByName() throws Exception {
        expectInvalidField("{\"description\":\"Body\",\"priority\":\"LOW\",\"category\":\"BILLING\"}", "title");
        expectInvalidField("""
                {"title":"  ","description":"Body","priority":"LOW","category":"BILLING"}
                """, "title");
        expectInvalidField("""
                {"title":"%s","description":"Body","priority":"LOW","category":"BILLING"}
                """.formatted("a".repeat(201)), "title");
        expectInvalidField("{\"title\":\"Title\",\"priority\":\"LOW\",\"category\":\"BILLING\"}", "description");
        expectInvalidField("""
                {"title":"Title","description":"  ","priority":"LOW","category":"BILLING"}
                """, "description");
        expectInvalidField("""
                {"title":"Title","description":"%s","priority":"LOW","category":"BILLING"}
                """.formatted("a".repeat(5001)), "description");
        expectInvalidField("""
                {"title":"Title","description":"Body","priority":"NOPE","category":"BILLING"}
                """, "priority");
        expectInvalidField("""
                {"title":"Title","description":"Body","priority":"LOW","category":"NOPE"}
                """, "category");
        expectInvalidField("""
                {"title":"Title","description":"Body","priority":"LOW","category":"BILLING","assignee":"%s"}
                """.formatted("a".repeat(101)), "assignee");
        assertThat(ticketCount()).isZero();
    }

    @Test
    void namesEveryInvalidFieldInOneResponse() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"NOPE\",\"category\":\"NOPE\",\"assignee\":\"%s\"}".formatted("a".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "description", "priority", "category", "assignee")));
    }

    @Test
    void rejectsAnUnknownFieldAsMalformed() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","description":"Body","priority":"LOW","category":"BILLING","status":"OPEN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("status")));
        assertThat(ticketCount()).isZero();
    }

    private void expectInvalidField(String body, String field) throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    private int ticketCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM ticket", Integer.class);
        return count == null ? 0 : count;
    }
}
