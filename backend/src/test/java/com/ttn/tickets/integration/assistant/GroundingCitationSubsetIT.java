package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
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
class GroundingCitationSubsetIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final VectorStore vectorStore;
    private final RecordingChatModel recordingChatModel;

    @Autowired
    GroundingCitationSubsetIT(
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
    void stubCitationAndReset() {
        recordingChatModel.reset();
        recordingChatModel.setStubResponse("Payment issue resolved per policy [TKT-9999].");
    }

    @Test
    void citationOutsideRetrievedSetFailsVerificationAfterSingleChatCall() throws Exception {
        vectorStore.add(List.of(new Document(
                "Customer reports duplicate charge on March invoice duplicate billing charge March invoice",
                Map.of("ticketId", "TKT-1001", "status", "OPEN", "chunkType", "body"))));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What happened with the duplicate billing charge?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CITATION_VERIFICATION_FAILED"));

        assertThat(recordingChatModel.getCallCount()).isEqualTo(1);
    }
}
