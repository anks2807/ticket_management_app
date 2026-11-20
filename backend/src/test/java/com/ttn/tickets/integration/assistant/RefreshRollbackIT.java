package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
class RefreshRollbackIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final RefreshIntentProcessor refreshIntentProcessor;

    @Autowired
    RefreshRollbackIT(
            DataSource dataSource,
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            RefreshIntentProcessor refreshIntentProcessor) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.refreshIntentProcessor = refreshIntentProcessor;
    }

    @AfterEach
    void dropTestTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS ttn_test_fail_intent ON ticket_refresh_intent");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS ttn_test_fail_intent()");
    }

    @Test
    void doesNotRefreshWhenTicketChangeRollsBackWithoutIntent() throws Exception {
        installIntentFailureTrigger();
        int intentsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_refresh_intent", Integer.class);
        int chunksBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM assistant_ticket_chunk", Integer.class);

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Rollback","description":"Should not index","priority":"LOW","category":"BILLING"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_refresh_intent", Integer.class))
                .isEqualTo(intentsBefore);
        refreshIntentProcessor.processOnce();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM assistant_ticket_chunk", Integer.class))
                .isEqualTo(chunksBefore);
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
}
