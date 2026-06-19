package com.example.Nosql_Homework.agent.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 向量化服务封装
 * 使用本地 all-MiniLM-L6-v2 模型将文本转为 384 维向量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 将文本转为浮点数数组
     */
    public float[] embed(String text) {
        return embeddingModel.embed(text).content().vector();
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
