package com.example.Nosql_Homework.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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

    @Value("${langchain4j.openai.max-retries:3}")
    private Integer chatMaxRetries;

    // ======= Embedding Model =======
    @Value("${tiger.rag.embedding.model-name}")
    private String embeddingModelName;

    @Value("${tiger.rag.embedding.dimensions}")
    private Integer embeddingDimensions;

    @Value("${tiger.rag.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${tiger.rag.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${tiger.rag.embedding.timeout-seconds:60}")
    private Integer embeddingTimeoutSeconds;

    @Value("${tiger.rag.embedding.max-retries:3}")
    private Integer embeddingMaxRetries;

    @Bean
    public OpenAiChatModel chatLanguageModel() {
        log.info("[LangChainConfig] init Chat LLM: baseUrl={}, model={}, temp={}, maxTokens={}, timeout={}s, maxRetries={}",
                baseUrl, modelName, temperature, maxTokens, timeoutSeconds, chatMaxRetries);

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(chatMaxRetries)
                .logRequests(true)
                .logResponses(true)
                .build();

        log.info("[LangChainConfig] Chat LLM ready: model={}", modelName);
        return model;
    }

    @Bean
    public OpenAiStreamingChatModel streamingChatLanguageModel() {
        log.info("[LangChainConfig] init Streaming Chat LLM: baseUrl={}, model={}, temp={}, maxTokens={}, timeout={}s",
                baseUrl, modelName, temperature, maxTokens, timeoutSeconds);

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();

        log.info("[LangChainConfig] Streaming Chat LLM ready: model={}", modelName);
        return model;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("[LangChainConfig] init Embedding: baseUrl={}, model={}, dim={}, timeout={}s, maxRetries={}",
                embeddingBaseUrl, embeddingModelName, embeddingDimensions, embeddingTimeoutSeconds, embeddingMaxRetries);

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .modelName(embeddingModelName)
                .dimensions(embeddingDimensions)
                .timeout(Duration.ofSeconds(embeddingTimeoutSeconds))
                .maxRetries(embeddingMaxRetries)
                .logRequests(true)
                .logResponses(true)
                .build();

        log.info("[LangChainConfig] Embedding ready: model={}", embeddingModelName);
        return model;
    }
}
