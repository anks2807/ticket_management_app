package com.ttn.tickets.assistant.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.ttn.tickets.assistant.support.DeterministicFakeEmbeddingModel;
import com.ttn.tickets.assistant.support.RecordingChatModel;

@AutoConfiguration
@Profile("integration-test")
public class AssistantIntegrationTestConfiguration {

    @Bean
    @Primary
    EmbeddingModel embeddingModel() {
        return new DeterministicFakeEmbeddingModel();
    }

    @Bean
    @Primary
    ChatModel chatModel() {
        return new RecordingChatModel();
    }

    @Bean
    RecordingChatModel recordingChatModel(ChatModel chatModel) {
        return (RecordingChatModel) chatModel;
    }
}
