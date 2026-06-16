package com.example.Nosql_Homework.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChainConfig {

    // ======= Chat LLM =======
    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.openai.model-name}")
    private String modelName;

    @Value("${langchain4j.openai.temperature}")
    private Double temperature;

    @Value("${langchain4j.openai.max-tokens}")
    private Integer maxTokens;

    @Value("${langchain4j.openai.timeout-seconds}")
    private Integer timeoutSeconds;

    // ======= Embedding Model (DeepSeek —— 与 Chat 走不同 BaseURL) =======
    @Value("${tiger.rag.embedding.model-name}")
    private String embeddingModelName;

    @Value("${tiger.rag.embedding.dimensions}")
    private Integer embeddingDimensions;

    @Value("${tiger.rag.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${tiger.rag.embedding.base-url}")
    private String embeddingBaseUrl;

    @Bean
    public OpenAiChatModel chatLanguageModel() {
        log.info("初始化 LLM: baseUrl={}, model={}, temperature={}, maxTokens={}, timeout={}s",
                baseUrl, modelName, temperature, maxTokens, timeoutSeconds);

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        log.info("LLM 初始化完成: modelName={}, apiKey 已配置={}", modelName, apiKey != null && !apiKey.isBlank() && !"unset".equals(apiKey));
        return model;
    }

    /**
     * Embedding 模型: 使用 DeepSeek (https://api.deepseek.com/v1)
     * 与 Chat LLM 使用不同的 base-url / api-key / model
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 Embedding 模型: baseUrl={}, model={}, dimensions={}",
                embeddingBaseUrl, embeddingModelName, embeddingDimensions);

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .modelName(embeddingModelName)
                .dimensions(embeddingDimensions)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        log.info("Embedding 初始化完成: modelName={}, apiKey 已配置={}",
                embeddingModelName, embeddingApiKey != null && !embeddingApiKey.isBlank());
        return model;
    }
}
