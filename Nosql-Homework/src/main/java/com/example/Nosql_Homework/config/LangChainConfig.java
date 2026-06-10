package com.example.Nosql_Homework.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChainConfig {

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
}
