package com.ttn.tickets.integration.assistant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ttn.tickets.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
class AskInvalidQuestionIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;

    @Autowired
    AskInvalidQuestionIT(DataSource dataSource, MockMvc mockMvc) {
        super(dataSource);
        this.mockMvc = mockMvc;
    }

    @Test
    void blankQuestionReturnsInvalidQuestion() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_QUESTION"));
    }

    @Test
    void overlongQuestionReturnsInvalidQuestion() throws Exception {
        String longQuestion = "x".repeat(1001);
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + longQuestion + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_QUESTION"));
    }
}
