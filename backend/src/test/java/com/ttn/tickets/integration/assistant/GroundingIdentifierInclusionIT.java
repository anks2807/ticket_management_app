package com.ttn.tickets.integration.assistant;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ttn.tickets.assistant.support.RecordingChatModel;
import com.ttn.tickets.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
class GroundingIdentifierInclusionIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final VectorStore vectorStore;
    private final RecordingChatModel recordingChatModel;

    @Autowired
    GroundingIdentifierInclusionIT(
            DataSource dataSource,
            MockMvc mockMvc,
            VectorStore vectorStore,
            RecordingChatModel recordingChatModel) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.vectorStore = vectorStore;
        this.recordingChatModel = recordingChatModel;
    }

    @BeforeEach
    void stubAndReset() {
        recordingChatModel.reset();
        recordingChatModel.setStubResponse("The shipment was delayed in customs [TKT-1001].");
    }

    @Test
    void forcedIdentifierInQuestionRetrievesTicketDespiteLowSimilarity() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Late delivery","description":"Package stuck in transit","priority":"HIGH","category":"SHIPPING"}
                                """))
                .andExpect(status().isCreated());

        vectorStore.add(List.of(new Document(
                "Late delivery Package stuck in transit category SHIPPING",
                Map.of(
                        "ticketId", "TKT-1001",
                        "status", "OPEN",
                        "priority", "HIGH",
                        "category", "SHIPPING",
                        "chunkType", "header"))));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Why is tkt-1001 still delayed?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("GROUNDED"))
                .andExpect(jsonPath("$.sources[*].ticketId", hasItem("TKT-1001")));
    }
}
