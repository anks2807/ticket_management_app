package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.integration.PostgresIntegrationTest;
import com.ttn.tickets.persistence.RefreshIntentRepository;

@AutoConfigureMockMvc
class RefreshProcessorIT extends PostgresIntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final RefreshIntentProcessor refreshIntentProcessor;
    private final RefreshIntentRepository refreshIntentRepository;

    @Autowired
    RefreshProcessorIT(
            DataSource dataSource,
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            RefreshIntentProcessor refreshIntentProcessor,
            RefreshIntentRepository refreshIntentRepository) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.refreshIntentProcessor = refreshIntentProcessor;
        this.refreshIntentRepository = refreshIntentRepository;
    }

    @Test
    void indexesTicketChunksWithMetadataAfterProcessingIntent() throws Exception {
        String publicId = createTicket("Payment declined", "Card authorization failed for invoice 8841");
        refreshIntentProcessor.processOnce();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ?",
                Integer.class,
                publicId)).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metadata->>'ticketId' FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ? LIMIT 1",
                String.class,
                publicId)).isEqualTo(publicId);
        for (String key : List.of("status", "priority", "assignee", "category", "chunkType")) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT metadata->>? IS NOT NULL FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ? LIMIT 1",
                    Boolean.class,
                    key,
                    publicId)).isTrue();
        }
        String indexedContent = jdbcTemplate.queryForObject(
                "SELECT content FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ? LIMIT 1",
                String.class,
                publicId);
        assertThat(indexedContent).contains("Card authorization failed");

        mockMvc.perform(patch("/api/tickets/" + publicId)
                        .contentType(MERGE_PATCH)
                        .content("{\"description\":\"Card authorization failed after processor run\"}"))
                .andExpect(status().isOk());
        refreshIntentProcessor.processOnce();

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ? LIMIT 1",
                String.class,
                publicId);
        assertThat(content).contains("after processor run");
    }

    @Test
    void collapsesMultiplePendingIntentsForSameTicketIntoOneIndex() throws Exception {
        String publicId = createTicket("Collapse", "Same ticket twice");
        mockMvc.perform(patch("/api/tickets/" + publicId)
                        .contentType(MERGE_PATCH)
                        .content("{\"description\":\"Edit to enqueue second intent\"}"))
                .andExpect(status().isOk());

        assertThat(refreshIntentRepository.findAll()).hasSize(2);
        refreshIntentProcessor.processOnce();

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ?",
                Integer.class,
                publicId);
        assertThat(chunkCount).isGreaterThanOrEqualTo(1);
        assertThat(refreshIntentRepository.findAll()).allMatch(i -> i.getProcessingStatus() == ProcessingStatus.DONE);
        assertThat(refreshIntentRepository.findAll()).hasSize(2);
    }

    private String createTicket(String title, String description) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","priority":"HIGH","category":"BILLING"}
                                """.formatted(title, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }
}
