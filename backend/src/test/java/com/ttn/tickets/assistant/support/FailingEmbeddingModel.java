package com.ttn.tickets.assistant.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class FailingEmbeddingModel extends DeterministicFakeEmbeddingModel {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        throw new RuntimeException("forced embedding failure");
    }

    @Override
    public float[] embed(Document document) {
        throw new RuntimeException("forced embedding failure");
    }
}
