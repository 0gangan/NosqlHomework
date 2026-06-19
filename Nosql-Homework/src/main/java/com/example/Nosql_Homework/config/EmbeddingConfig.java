package com.example.Nosql_Homework.config;

import com.example.Nosql_Homework.agent.embedding.FallbackEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Embedding 配置 — DeepSeek API + 本地关键词双层降级
 * <p>
 * 优先调用 DeepSeek Embedding API，失败自动降级为本地 SimpleEmbeddingModel
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    @Value("${langchain4j.embedding.api-key}")
    private String apiKey;

    @Value("${langchain4j.embedding.base-url}")
    private String baseUrl;

    @Value("${langchain4j.embedding.model-name}")
    private String modelName;

    @Value("${langchain4j.openai.timeout-seconds}")
    private Integer timeoutSeconds;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 Embedding (API + 本地降级): model={}, baseUrl={}", modelName, baseUrl);

        OpenAiEmbeddingModel apiModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        FallbackEmbeddingModel fallbackModel = new FallbackEmbeddingModel(apiModel);

        log.info("Embedding 初始化完成: apiKey已配置={}, 降级模型=SimpleEmbeddingModel",
                apiKey != null && !apiKey.isBlank() && !"unset".equals(apiKey));
        return fallbackModel;
    }
}
