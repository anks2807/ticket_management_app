package com.ttn.tickets.assistant.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.bedrock.converse.api.BedrockCacheStrategy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

import io.micrometer.observation.ObservationRegistry;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@Profile("!integration-test")
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${assistant.bedrock.chat-model-id:}')")
public class AssistantBedrockChatConfiguration {

    @Bean
    String assistantGroundingSystemPrompt(ResourceLoader resourceLoader) throws IOException {
        return resourceLoader
                .getResource("classpath:assistant/grounding-system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Bean
    @Primary
    ChatModel assistantBedrockChatModel(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
            AssistantProperties assistantProperties,
            ObjectProvider<ToolCallingManager> toolCallingManager,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        var optionsBuilder = BedrockChatOptions.builder()
                .model(assistantProperties.getBedrock().getChatModelId());
        if (assistantProperties.getBedrock().getPromptCaching().isEnabled()) {
            optionsBuilder.cacheOptions(BedrockCacheOptions.builder()
                    .strategy(BedrockCacheStrategy.SYSTEM_ONLY)
                    .multiBlockSystemCaching(true)
                    .build());
        } else {
            optionsBuilder.cacheOptions(BedrockCacheOptions.builder()
                    .strategy(BedrockCacheStrategy.NONE)
                    .build());
        }
        return BedrockProxyChatModel.builder()
                .bedrockRuntimeClient(bedrockRuntimeClient)
                .bedrockRuntimeAsyncClient(bedrockRuntimeAsyncClient)
                .options(optionsBuilder.build())
                .toolCallingManager(toolCallingManager.getIfAvailable())
                .observationRegistry(observationRegistry.getIfAvailable(ObservationRegistry::create))
                .build();
    }
}
