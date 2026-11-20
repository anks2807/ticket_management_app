package com.ttn.tickets.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ttn.tickets.assistant.refresh.RefreshIntentProcessor;
import com.ttn.tickets.assistant.support.RecordingChatModel;
import com.ttn.tickets.assistant.retrieval.AskRetrievalService;
import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.integration.PostgresIntegrationTest;
import com.ttn.tickets.persistence.RefreshIntentRepository;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("llm")
@Import(LlmRetrievalEvaluationIT.LlmSupportConfig.class)
@AutoConfigureMockMvc
class LlmRetrievalEvaluationIT extends PostgresIntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final AskRetrievalService askRetrievalService;
    private final RefreshIntentProcessor refreshIntentProcessor;
    private final RefreshIntentRepository refreshIntentRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, String> seededTicketIds = new HashMap<>();

    @DynamicPropertySource
    static void useProductionEmbeddingBeans(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "com.ttn.tickets.assistant.config.AssistantIntegrationTestConfiguration");
        registry.add("spring.ai.model.embedding", () -> "bedrock");
        registry.add("spring.ai.model.chat", () -> "none");
    }

    @Autowired
    LlmRetrievalEvaluationIT(
            DataSource dataSource,
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            AskRetrievalService askRetrievalService,
            RefreshIntentProcessor refreshIntentProcessor,
            RefreshIntentRepository refreshIntentRepository,
            ObjectMapper objectMapper) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.askRetrievalService = askRetrievalService;
        this.refreshIntentProcessor = refreshIntentProcessor;
        this.refreshIntentRepository = refreshIntentRepository;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void requireBedrockCredentials() throws Exception {
        Assumptions.assumeTrue(
                bedrockCredentialsAvailable(),
                "AWS credentials and region required for @Tag(\"llm\") retrieval evaluation");
        seededTicketIds.clear();
        seedEvaluationTickets();
        drainPendingIntents();
    }

    @Test
    void reportsHitRateAtKForEvaluationQuestions() throws IOException {
        List<EvaluationCase> cases = loadEvaluationCases();
        int hits = 0;
        for (EvaluationCase evaluationCase : cases) {
            String expectedTicketId = seededTicketIds.get(evaluationCase.expectedSeedKey());
            assertThat(expectedTicketId).as("seed key %s", evaluationCase.expectedSeedKey()).isNotNull();
            Set<String> retrieved = new HashSet<>();
            askRetrievalService.retrieve(evaluationCase.question()).sources().forEach(source ->
                    retrieved.add(source.ticketId()));
            boolean hit = retrieved.contains(expectedTicketId);
            if (hit) {
                hits++;
            }
        }
        double hitRate = (double) hits / cases.size();
        assertThat(hitRate).as("retrieval hit rate @K for assistant/evaluation-questions.json").isGreaterThanOrEqualTo(0.5);
    }

    private List<EvaluationCase> loadEvaluationCases() throws IOException {
        ClassPathResource resource = new ClassPathResource("assistant/evaluation-questions.json");
        try (InputStream stream = resource.getInputStream()) {
            return objectMapper.readValue(stream, new TypeReference<>() {});
        }
    }

    private void seedEvaluationTickets() throws Exception {
        MvcResult paymentCreated = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Payment declined","description":"Customer payment authorization failed for invoice","priority":"HIGH","category":"BILLING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentTicketId = JsonPath.read(paymentCreated.getResponse().getContentAsString(), "$.id");
        seededTicketIds.put("payment-high", paymentTicketId);

        MvcResult shippingCreated = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resolved shipment","description":"Similar resolved delivery after customs delay","priority":"MEDIUM","category":"SHIPPING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String shippingTicketId = JsonPath.read(shippingCreated.getResponse().getContentAsString(), "$.id");
        seededTicketIds.put("resolved-shipping", shippingTicketId);

        mockMvc.perform(patch("/api/tickets/" + shippingTicketId)
                        .contentType(MERGE_PATCH)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/tickets/" + shippingTicketId)
                        .contentType(MERGE_PATCH)
                        .content("""
                                {"status":"RESOLVED","resolutionNotes":"Similar resolved customs clearance case"}
                                """))
                .andExpect(status().isOk());
        refreshIntentProcessor.processOnce();
        refreshIntentProcessor.processOnce();
    }

    private void drainPendingIntents() {
        int guard = 0;
        while (refreshIntentRepository.countByProcessingStatus(ProcessingStatus.PENDING) > 0 && guard < 20) {
            refreshIntentProcessor.processOnce();
            guard++;
        }
    }

    private static boolean bedrockCredentialsAvailable() {
        try {
            DefaultCredentialsProvider.create().resolveCredentials();
            var region = DefaultAwsRegionProviderChain.builder().build().getRegion();
            return region != null && !region.id().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private record EvaluationCase(String question, String expectedSeedKey) {}

    @TestConfiguration
    static class LlmSupportConfig {
        @Bean
        @Primary
        ChatModel chatModel() {
            return new RecordingChatModel();
        }
    }
}
