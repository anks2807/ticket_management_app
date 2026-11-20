package com.ttn.tickets.integration.assistant;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.assistant.support.RecordingChatModel;
import com.ttn.tickets.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
class MissingTicketIdentifierIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final RefreshIntentProcessor refreshIntentProcessor;
    private final RecordingChatModel recordingChatModel;

    @Autowired
    MissingTicketIdentifierIT(
            DataSource dataSource,
            MockMvc mockMvc,
            RefreshIntentProcessor refreshIntentProcessor,
            RecordingChatModel recordingChatModel) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.refreshIntentProcessor = refreshIntentProcessor;
        this.recordingChatModel = recordingChatModel;
    }

    @BeforeEach
    void resetStub() {
        recordingChatModel.reset();
    }

    @Test
    void groundedAnswerDoesNotCiteNonExistentTicketMentionedInQuestion() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Payment declined","description":"Customer payment authorization failed","priority":"HIGH","category":"BILLING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String ticketId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        recordingChatModel.setStubResponse(
                "Payment authorization issue noted [" + ticketId + "].");
        refreshIntentProcessor.processOnce();

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What about TKT-9999 and payment authorization failed?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("GROUNDED"))
                .andExpect(jsonPath("$.sources[*].ticketId", hasItem(ticketId)))
                .andExpect(jsonPath("$.sources[*].ticketId", not(hasItem("TKT-9999"))));
    }
}
