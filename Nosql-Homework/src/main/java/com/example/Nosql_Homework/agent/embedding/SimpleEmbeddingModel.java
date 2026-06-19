package com.example.Nosql_Homework.agent.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 轻量级本地 Embedding 模型
 * <p>
 * 基于关键词 Jaccard 相似度 + TF 加权，将文本映射到固定维度的稀疏向量。
 * 优点: 零外部依赖，毫秒级速度，适合课程演示。
 * <p>
 * 原理: 提取文本中的中英文词 → 哈希到 384 维 → 与全局词表求 Jaccard 相似度
 */
public class SimpleEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 384;

    /**
     * 全局高频词表 (中英文) — 用于构建稀疏向量
     */
    private static final List<String> VOCAB = buildVocab();

    private static List<String> buildVocab() {
        List<String> words = new ArrayList<>();
        // 中文高频关键词
        words.addAll(List.of(
                "项目", "语言", "星标", "品类", "增长", "排名", "活跃", "分析",
                "报告", "热度", "月报", "趋势", "分布", "新增", "贡献", "框架",
                "开源", "数据", "排行", "平均", "总量", "占比", "最快", "下降",
                "Python", "Java", "JavaScript", "TypeScript", "Go", "Rust", "C++", "Ruby",
                "AI", "Web", "Tool", "Mobile", "Data", "DevOps", "Security", "Automation",
                "DeepSeek", "browser", "Ollama", "LangChain", "agent", "LLM",
                "机器学习", "深度学习", "自然语言", "浏览器", "自动化", "聊天",
                "项目数", "最火", "热门", "流行", "高星标", "top", "best",
                "Q1", "Q2", "month", "quarter"
        ));
        // 英文补充
        words.addAll(List.of(
                "project", "language", "stars", "category", "growth", "ranking",
                "active", "analysis", "report", "trend", "monthly", "framework",
                "opensource", "data", "average", "total", "fastest", "decline",
                "machine", "learning", "deep", "browser", "automation", "chat",
                "agent", "model", "copilot", "assistant", "generate"
        ));
        return words;
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Response<Embedding> embed(String text) {
        float[] vector = encode(text);
        return Response.from(Embedding.from(vector));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = textSegments.stream()
                .map(ts -> Embedding.from(encode(ts.text())))
                .collect(Collectors.toList());
        return Response.from(embeddings);
    }

    /**
     * 将文本编码为 384 维向量
     * 算法: 提取关键词 → 对每个词取 hash % DIM → 在对应维度累加 TF 权重
     */
    private float[] encode(String text) {
        float[] vec = new float[DIM];

        if (text == null || text.isBlank()) {
            return vec;
        }

        // 提取关键词: 中英文词 / 2-gram
        Set<String> tokens = extractTokens(text.toLowerCase());

        // 对每个 token 哈希到向量维度
        for (String token : tokens) {
            int idx = Math.abs(token.hashCode()) % DIM;
            vec[idx] += 1.0f;

            // 对词表中的词额外加权
            if (VOCAB.contains(token)) {
                int idx2 = Math.abs(("vocab_" + token).hashCode()) % DIM;
                vec[idx2] += 2.0f;
            }
        }

        // L2 归一化
        double norm = 0;
        for (float v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        }

        return vec;
    }

    /**
     * 从文本中提取关键词
     */
    private Set<String> extractTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();

        // 1. 提取中文词 (2-gram, 3-gram)
        StringBuilder chinese = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chinese.append(c);
            } else {
                if (chinese.length() >= 2) {
                    String s = chinese.toString();
                    for (int i = 0; i <= s.length() - 2; i++) {
                        tokens.add(s.substring(i, i + 2));
                        if (i + 3 <= s.length()) {
                            tokens.add(s.substring(i, i + 3));
                        }
                    }
                }
                chinese.setLength(0);
            }
        }
        if (chinese.length() >= 2) {
            String s = chinese.toString();
            for (int i = 0; i <= s.length() - 2; i++) {
                tokens.add(s.substring(i, i + 2));
                if (i + 3 <= s.length()) {
                    tokens.add(s.substring(i, i + 3));
                }
            }
        }

        // 2. 提取英文单词和数字
        String[] words = text.split("[^a-z0-9]+");
        for (String w : words) {
            if (w.length() >= 2) {
                tokens.add(w);
            }
        }

        // 3. 匹配词表中的复合词
        for (String vocab : VOCAB) {
            if (text.contains(vocab.toLowerCase())) {
                tokens.add(vocab.toLowerCase());
            }
        }

        return tokens;
    }
}
