package com.ttn.tickets.assistant.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    private final Ask ask = new Ask();
    private final Retrieval retrieval = new Retrieval();
    private final Chunking chunking = new Chunking();
    private final Refresh refresh = new Refresh();
    private final Bedrock bedrock = new Bedrock();

    public Ask getAsk() {
        return ask;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Chunking getChunking() {
        return chunking;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public Bedrock getBedrock() {
        return bedrock;
    }

    public static class Ask {

        private int maxQuestionLength = 1000;

        public int getMaxQuestionLength() {
            return maxQuestionLength;
        }

        public void setMaxQuestionLength(int maxQuestionLength) {
            this.maxQuestionLength = maxQuestionLength;
        }
    }

    public static class Retrieval {

        private int topK = 5;
        private double similarityThreshold = 0.70;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }
    }

    public static class Chunking {

        private int maxTokens = 800;

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class Refresh {

        private Duration pollInterval = Duration.ofSeconds(5);
        private int batchSize = 20;
        private int maxAttempts = 5;
        private Duration backoffBase = Duration.ofSeconds(30);

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoffBase() {
            return backoffBase;
        }

        public void setBackoffBase(Duration backoffBase) {
            this.backoffBase = backoffBase;
        }
    }

    public static class Bedrock {

        private String chatModelId;
        private String embeddingModelId = "amazon.titan-embed-text-v2:0";
        private int embeddingDimensions = 1024;
        private final PromptCaching promptCaching = new PromptCaching();

        public String getChatModelId() {
            return chatModelId;
        }

        public void setChatModelId(String chatModelId) {
            this.chatModelId = chatModelId;
        }

        public String getEmbeddingModelId() {
            return embeddingModelId;
        }

        public void setEmbeddingModelId(String embeddingModelId) {
            this.embeddingModelId = embeddingModelId;
        }

        public int getEmbeddingDimensions() {
            return embeddingDimensions;
        }

        public void setEmbeddingDimensions(int embeddingDimensions) {
            this.embeddingDimensions = embeddingDimensions;
        }

        public PromptCaching getPromptCaching() {
            return promptCaching;
        }

        public static class PromptCaching {

            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
