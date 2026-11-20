package com.ttn.tickets.assistant.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

public class RecordingChatModel implements ChatModel {

    private final AtomicInteger callCount = new AtomicInteger();
    private final List<Prompt> prompts = new ArrayList<>();
    private volatile String stubResponse = "Grounded answer [TKT-1].";

    @Override
    public ChatResponse call(Prompt prompt) {
        callCount.incrementAndGet();
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(stubResponse))));
    }

    public int getCallCount() {
        return callCount.get();
    }

    public Prompt getLastPrompt() {
        synchronized (prompts) {
            return prompts.isEmpty() ? null : prompts.getLast();
        }
    }

    public void setStubResponse(String stubResponse) {
        this.stubResponse = stubResponse;
    }

    public void reset() {
        callCount.set(0);
        synchronized (prompts) {
            prompts.clear();
        }
    }
}
