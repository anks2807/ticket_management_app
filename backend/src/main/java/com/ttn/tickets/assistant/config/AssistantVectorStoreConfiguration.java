package com.ttn.tickets.assistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AssistantVectorStoreConfiguration {

    @Bean
    @Primary
    VectorStore assistantVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            AssistantProperties assistantProperties,
            @Value("${spring.ai.vectorstore.pgvector.table-name:assistant_ticket_chunk}") String tableName) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(assistantProperties.getBedrock().getEmbeddingDimensions())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .vectorTableName(tableName)
                .build();
    }
}
