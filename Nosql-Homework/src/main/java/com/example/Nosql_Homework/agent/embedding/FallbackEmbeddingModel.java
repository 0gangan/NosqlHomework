package com.example.Nosql_Homework.agent.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 双路 Embedding 模型: 优先 DeepSeek API，失败自动降级为本地 SimpleEmbeddingModel
 * <p>
 * 对上层完全透明——AgentService 无需感知降级逻辑
 */
@Slf4j
public class FallbackEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel primary;   // DeepSeek API
    private final EmbeddingModel fallback;  // 本地关键词
    private volatile boolean primaryHealthy = true;

    public FallbackEmbeddingModel(EmbeddingModel primary) {
        this.primary = primary;
        this.fallback = new SimpleEmbeddingModel();
    }

    @Override
    public Response<Embedding> embed(String text) {
        if (primaryHealthy) {
            try {
                return primary.embed(text);
            } catch (Exception e) {
                log.warn("Embedding API 调用失败, 自动降级为本地模型: {}", e.toString());
                primaryHealthy = false;
            }
        }
        return fallback.embed(text);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (primaryHealthy) {
            try {
                return primary.embedAll(textSegments);
            } catch (Exception e) {
                log.warn("Embedding API 批量调用失败, 自动降级为本地模型: {}", e.toString());
                primaryHealthy = false;
            }
        }
        return fallback.embedAll(textSegments);
    }
}
