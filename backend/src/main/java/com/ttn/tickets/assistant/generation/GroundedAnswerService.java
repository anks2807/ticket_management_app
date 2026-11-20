package com.ttn.tickets.assistant.generation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class GroundedAnswerService {

    private final ChatModel chatModel;
    private final String systemPrompt;

    public GroundedAnswerService(ChatModel chatModel, ResourceLoader resourceLoader) throws IOException {
        this.chatModel = chatModel;
        this.systemPrompt = resourceLoader
                .getResource("classpath:assistant/grounding-system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public String generate(String question, List<Document> context) {
        String contextBlock = context.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        String userText = """
                Question:
                %s

                Ticket context:
                %s
                """.formatted(question, contextBlock);
        return chatModel.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userText))))
                .getResult()
                .getOutput()
                .getText();
    }
}