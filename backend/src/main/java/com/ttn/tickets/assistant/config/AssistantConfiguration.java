package com.ttn.tickets.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AssistantProperties.class)
@Import({AssistantVectorStoreConfiguration.class, AssistantBedrockChatConfiguration.class})
public class AssistantConfiguration {
}
