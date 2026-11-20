package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.integration.PostgresIntegrationTest;
import com.ttn.tickets.persistence.RefreshIntentRepository;

@AutoConfigureMockMvc
class RefreshStatusIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final RefreshIntentRepository refreshIntentRepository;

    @Autowired
    RefreshStatusIT(
            DataSource dataSource,
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            RefreshIntentRepository refreshIntentRepository) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.refreshIntentRepository = refreshIntentRepository;
    }

    @Test
    void pendingAndFailedCountsMatchDatabase() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Status A","description":"First ticket","priority":"LOW","category":"TECHNICAL"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Status B","description":"Second ticket","priority":"LOW","category":"TECHNICAL"}
                                """))
                .andExpect(status().isCreated());

        jdbcTemplate.update(
                """
                UPDATE ticket_refresh_intent
                SET processing_status = 'FAILED', attempt_count = 5, last_error = 'test failure'
                WHERE id = (SELECT MIN(id) FROM ticket_refresh_intent)
                """);

        long pending = refreshIntentRepository.countByProcessingStatus(ProcessingStatus.PENDING);
        long failed = refreshIntentRepository.countByProcessingStatus(ProcessingStatus.FAILED);
        assertThat(pending).isGreaterThan(0);
        assertThat(failed).isEqualTo(1);

        mockMvc.perform(get("/api/ai/refresh/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value((int) pending))
                .andExpect(jsonPath("$.failed").value((int) failed));
    }
}
