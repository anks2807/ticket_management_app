package com.ttn.tickets.assistant.retrieval;

import org.springframework.stereotype.Component;

import com.ttn.tickets.assistant.config.AssistantProperties;

@Component
public class QuestionValidator {

    private final AssistantProperties assistantProperties;

    public QuestionValidator(AssistantProperties assistantProperties) {
        this.assistantProperties = assistantProperties;
    }

    public String validate(String question) {
        if (question == null || question.isBlank()) {
            throw new InvalidQuestionException("Question must not be blank");
        }
        String trimmed = question.strip();
        int maxLength = assistantProperties.getAsk().getMaxQuestionLength();
        if (trimmed.length() > maxLength) {
            throw new InvalidQuestionException("Question must be at most " + maxLength + " characters");
        }
        return trimmed;
    }
}