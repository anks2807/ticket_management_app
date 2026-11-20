package com.ttn.tickets.assistant.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class DeterministicFakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1024;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(vectorFor(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorFor(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String[] tokens = text.toLowerCase().split("\\W+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            long seed = token.hashCode();
            Random random = new Random(seed);
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] += random.nextFloat() * 2.0f - 1.0f;
            }
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] /= (float) norm;
            }
        }
        return vector;
    }
}
