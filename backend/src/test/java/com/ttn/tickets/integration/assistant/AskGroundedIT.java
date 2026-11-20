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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.assistant.support.RecordingChatModel;
import com.ttn.tickets.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
class AskGroundedIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final RefreshIntentProcessor refreshIntentProcessor;
    private final RecordingChatModel recordingChatModel;

    @Autowired
    AskGroundedIT(
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
    void paymentQuestionGroundsWithCitationsFromRetrievedSet() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Payment declined","description":"Customer payment authorization failed","priority":"HIGH","category":"BILLING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String ticketId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        recordingChatModel.setStubResponse("Payment issue resolved after card reauthorization [" + ticketId + "].");
        refreshIntentProcessor.processOnce();

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Which high-priority tickets are related to payment authorization failed?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("GROUNDED"))
                .andExpect(jsonPath("$.answer").exists())
                .andExpect(jsonPath("$.sources[*].ticketId", hasItem(ticketId)));

        assertThat(recordingChatModel.getCallCount()).isEqualTo(1);
    }

    @Test
    void shipmentScenarioGroundsWithRetrievedSourcesSubset() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Late delivery","description":"Package stuck in transit customs clearance","priority":"MEDIUM","category":"SHIPPING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String ticketId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        recordingChatModel.setStubResponse("Shipment delayed in customs clearance [" + ticketId + "].");
        refreshIntentProcessor.processOnce();

        var result = mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Show me similar issues with package stuck in transit customs clearance"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("GROUNDED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(ticketId);
        assertThat(recordingChatModel.getCallCount()).isEqualTo(1);
    }
}
