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
class GroundingNoMatchIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final VectorStore vectorStore;
    private final RecordingChatModel recordingChatModel;

    @Autowired
    GroundingNoMatchIT(
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
    void resetChatRecorder() {
        recordingChatModel.reset();
    }

    @Test
    void unrelatedQuestionReturnsNoRelevantTicketsWithoutCallingChat() throws Exception {
        vectorStore.add(List.of(new Document(
                "Billing invoice mismatch for enterprise client",
                Map.of("ticketId", "TKT-1001", "status", "OPEN", "chunkType", "body"))));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"How do I configure Kubernetes ingress controllers?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_RELEVANT_TICKETS"))
                .andExpect(jsonPath("$.answer").doesNotExist());

        assertThat(recordingChatModel.getCallCount()).isZero();
    }

    @Test
    void emptyIndexReturnsNoRelevantTicketsWithoutCallingChat() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Any open billing tickets?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_RELEVANT_TICKETS"))
                .andExpect(jsonPath("$.answer").doesNotExist());

        assertThat(recordingChatModel.getCallCount()).isZero();
    }

    @Test
    void strictSimilarityThresholdExcludesWeakMatchesWithoutCallingChat() throws Exception {
        vectorStore.add(List.of(new Document(
                "Quantum entanglement research notes unrelated topic",
                Map.of("ticketId", "TKT-2002", "status", "OPEN", "chunkType", "body"))));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"billing invoice payment duplicate charge"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_RELEVANT_TICKETS"));

        assertThat(recordingChatModel.getCallCount()).isZero();
    }
}
