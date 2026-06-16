package com.example.Nosql_Homework.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量生成服务
 *
 * 核心能力:
 * 1. 把单个字符串 (标题+内容) 转换为 float 向量 (embedding)
 * 2. 批量生成向量 (分批调用 Embedding API，避免一次性请求过大)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Value("${tiger.rag.embedding.model-name:doubao-embedding-16k}")
    private String embeddingModelName;

    @Value("${tiger.rag.embedding.batch-size:20}")
    private int batchSize;

    /**
     * 生成单段文本的向量
     *
     * @param text 要转向量的文本 (建议把 title + 内容拼装好后传入)
     * @return float 向量列表 (长度 = 模型维度，例如 1024)
     */
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        Response<Embedding> resp = embeddingModel.embed(text);
        Embedding emb = resp.content();
        return floatArrayToFloatList(emb.vector());
    }

    /**
     * 批量生成向量 —— 用于一次性给 N 个文档生成 embedding
     * 返回的 List 顺序与入参 texts 一一对应
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();

        List<List<Float>> results = new ArrayList<>(texts.size());

        int total = texts.size();
        int batches = (total + batchSize - 1) / batchSize;
        for (int i = 0; i < batches; i++) {
            int start = i * batchSize;
            int end = Math.min(start + batchSize, total);
            List<String> batch = texts.subList(start, end);

            log.debug("[Embedding] 批 {}/{} 文本 {} 条", i + 1, batches, batch.size());

            try {
                List<TextSegment> segments = batch.stream()
                        .map(TextSegment::from)
                        .collect(Collectors.toList());
                Response<List<Embedding>> resp = embeddingModel.embedAll(segments);
                List<Embedding> embeddings = resp.content();

                for (Embedding emb : embeddings) {
                    results.add(floatArrayToFloatList(emb.vector()));
                }
            } catch (Exception e) {
                log.error("[Embedding] 批 {} 失败: {}", i + 1, e.getMessage(), e);
                for (int j = 0; j < batch.size(); j++) {
                    results.add(new ArrayList<>());
                }
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("[Embedding] 完成，共 {} 条文本，模型={}", results.size(), embeddingModelName);
        return results;
    }

    /** 当前使用的模型名，方便写回 document 里 */
    public String getModelName() {
        return embeddingModelName;
    }

    // =============== helper ===============

    private static List<Float> floatArrayToFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }
}
