package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.assistant.support.FailingEmbeddingModel;
import com.ttn.tickets.assistant.support.RecordingChatModel;
import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(RefreshRetryFailedIT.FailingEmbedConfig.class)
class RefreshRetryFailedIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final RefreshIntentProcessor refreshIntentProcessor;

    @DynamicPropertySource
    static void refreshTuning(DynamicPropertyRegistry registry) {
        registry.add("assistant.refresh.max-attempts", () -> 3);
        registry.add("assistant.refresh.backoff-base", () -> "10s");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "com.ttn.tickets.assistant.config.AssistantIntegrationTestConfiguration");
    }

    @Autowired
    RefreshRetryFailedIT(
            DataSource dataSource,
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            RefreshIntentProcessor refreshIntentProcessor) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.refreshIntentProcessor = refreshIntentProcessor;
    }

    @Test
    void marksIntentFailedAfterRetriesWithBackoff() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Retry","description":"Embedding failure path","priority":"LOW","category":"TECHNICAL"}
                                """))
                .andExpect(status().isCreated());

        refreshIntentProcessor.processOnce();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM ticket_refresh_intent", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_attempt_at IS NOT NULL FROM ticket_refresh_intent", Boolean.class)).isTrue();

        jdbcTemplate.update("UPDATE ticket_refresh_intent SET next_attempt_at = NOW() - INTERVAL '1 second'");
        refreshIntentProcessor.processOnce();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM ticket_refresh_intent", Integer.class)).isEqualTo(2);

        jdbcTemplate.update("UPDATE ticket_refresh_intent SET next_attempt_at = NOW() - INTERVAL '1 second'");
        refreshIntentProcessor.processOnce();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT processing_status FROM ticket_refresh_intent", String.class))
                .isEqualTo(ProcessingStatus.FAILED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM ticket_refresh_intent", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error FROM ticket_refresh_intent", String.class)).contains("forced embedding");
    }

    @TestConfiguration
    static class FailingEmbedConfig {
        @Bean
        @Primary
        EmbeddingModel embeddingModel() {
            return new FailingEmbeddingModel();
        }

        @Bean
        @Primary
        ChatModel chatModel() {
            return new RecordingChatModel();
        }
    }
}
