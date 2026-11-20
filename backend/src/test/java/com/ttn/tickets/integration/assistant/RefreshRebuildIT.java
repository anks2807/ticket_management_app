package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.assistant.support.RecordingChatModel;
import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.integration.PostgresIntegrationTest;
import com.ttn.tickets.persistence.RefreshIntentRepository;

@AutoConfigureMockMvc
class RefreshRebuildIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final RefreshIntentRepository refreshIntentRepository;
    private final RefreshIntentProcessor refreshIntentProcessor;
    private final RecordingChatModel recordingChatModel;

    @Autowired
    RefreshRebuildIT(
            DataSource dataSource,
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            RefreshIntentRepository refreshIntentRepository,
            RefreshIntentProcessor refreshIntentProcessor,
            RecordingChatModel recordingChatModel) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.refreshIntentRepository = refreshIntentRepository;
        this.refreshIntentProcessor = refreshIntentProcessor;
        this.recordingChatModel = recordingChatModel;
    }

    @BeforeEach
    void resetStub() {
        recordingChatModel.reset();
    }

    @Test
    void rebuildAcceptsAndEnqueuesOnePendingIntentPerTicket() throws Exception {
        String paymentTicketId = createTicket(
                "Payment declined",
                "Customer payment authorization failed",
                "HIGH",
                "BILLING");
        String shippingTicketId = createTicket(
                "Resolved delivery",
                "Package delivered after customs delay",
                "MEDIUM",
                "SHIPPING");

        mockMvc.perform(post("/api/ai/refresh/rebuild")).andExpect(status().isAccepted());

        Long rebuildIntents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE change_type = 'FIELDS_UPDATED'",
                Long.class);
        assertThat(rebuildIntents).isEqualTo(2);

        drainPendingIntents();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ?",
                        Integer.class,
                        paymentTicketId))
                .isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM assistant_ticket_chunk WHERE metadata->>'ticketId' = ?",
                        Integer.class,
                        shippingTicketId))
                .isGreaterThan(0);

        recordingChatModel.setStubResponse(
                "High-priority payment issue noted [" + paymentTicketId + "].");
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Which high-priority tickets are related to payment authorization failed?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("GROUNDED"))
                .andExpect(jsonPath("$.sources[*].ticketId", hasItem(paymentTicketId)));

        recordingChatModel.setStubResponse(
                "Package customs delay noted [" + shippingTicketId + "].");
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Show issues with package delivered after customs delay"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("GROUNDED"))
                .andExpect(jsonPath("$.sources[*].ticketId", hasItem(shippingTicketId)));
    }

    private String createTicket(String title, String description, String priority, String category)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","priority":"%s","category":"%s"}
                                """.formatted(title, description, priority, category)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private void drainPendingIntents() {
        int guard = 0;
        while (refreshIntentRepository.countByProcessingStatus(ProcessingStatus.PENDING) > 0 && guard < 20) {
            refreshIntentProcessor.processOnce();
            guard++;
        }
        assertThat(refreshIntentRepository.countByProcessingStatus(ProcessingStatus.PENDING)).isZero();
    }
}
