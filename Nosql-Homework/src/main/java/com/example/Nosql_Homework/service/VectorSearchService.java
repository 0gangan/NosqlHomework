package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.entity.Project;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * MongoDB Atlas Vector Search 检索服务 —— Tiger-RAG 使用的向量数据库
 *
 * 核心逻辑:
 * 1. 用户问题 -> 调用豆包 Embedding API 生成 query vector
 * 2. 对 projects 集合执行 $vectorSearch 做 ANN 近似最邻近搜索
 * 3. 结合 $meta "vectorSearchScore" 拿到余弦相似度，返回 topK 个项目
 *
 * 前提:
 *   - 后端会给每个 project 的 description/topics/language 等字段生成 1024 维 embedding
 *   - 在 MongoDB Atlas 控制台针对 projects 集合创建向量搜索索引:
 *       索引名: tiger_projects_vector_index
 *       字段:   embedding (knnVector, 维度 1024, similarity cosine)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final MongoTemplate mongoTemplate;
    private final EmbeddingService embeddingService;

    @Value("${tiger.rag.vector-index-name:tiger_projects_vector_index}")
    private String vectorIndexName;

    @Value("${tiger.rag.top-k:5}")
    private int defaultTopK;

    @Value("${tiger.rag.num-candidates:100}")
    private int defaultNumCandidates;

    @Value("${tiger.rag.min-score:0.5}")
    private double defaultMinScore;

    /** 直接使用项目集合做向量检索 */
    private static final String COLLECTION = "projects";

    /**
     * 根据用户自然语言问题，在 projects 向量库中检索与之最相关的项目
     */
    public List<ScoredProject> searchByText(String query, Integer topK, Double minScore) {
        int k = topK != null ? topK : defaultTopK;
        double ms = minScore != null ? minScore : defaultMinScore;

        if (query == null || query.isBlank()) return new ArrayList<>();

        log.info("[VectorSearch] text={}, topK={}, minScore={}", truncate(query, 80), k, ms);

        List<Float> queryVector = embeddingService.embed(query);
        if (queryVector == null || queryVector.isEmpty()) {
            log.warn("[VectorSearch] embedding 返回空向量，跳过检索");
            return new ArrayList<>();
        }

        return doVectorSearch(queryVector, k, ms, defaultNumCandidates);
    }

    /**
     * 直接给已算好的向量做检索 (开放给测试/外部系统使用)
     */
    public List<ScoredProject> searchByVector(List<Float> queryVector, Integer topK, Double minScore) {
        if (queryVector == null || queryVector.isEmpty()) return new ArrayList<>();
        int k = topK != null ? topK : defaultTopK;
        double ms = minScore != null ? minScore : defaultMinScore;
        return doVectorSearch(queryVector, k, ms, defaultNumCandidates);
    }

    // ========== 内部实现 ==========

    private List<ScoredProject> doVectorSearch(List<Float> queryVector, int topK, double minScore, int numCandidates) {
        MongoDatabase db = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase();
        MongoCollection<Document> col = db.getCollection(COLLECTION);

        // List<Float> -> List<Double>（MongoDB 驱动对 float 也支持，但 double 更通用）
        List<Double> vec = new ArrayList<>(queryVector.size());
        for (Float f : queryVector) vec.add(f.doubleValue());

        Document vectorSearch = new Document("$vectorSearch",
                new Document("index", vectorIndexName)
                        .append("path", "embedding")
                        .append("queryVector", vec)
                        .append("numCandidates", numCandidates)
                        .append("limit", topK)
        );

        Document addFields = new Document("$addFields",
                new Document("score", new Document("$meta", "vectorSearchScore"))
        );

        Document match = new Document("$match",
                new Document("score", new Document("$gte", minScore))
        );

        Document sort = new Document("$sort", new Document("score", -1));
        Document limit = new Document("$limit", topK);

        // 只拿构造回答所需的字段
        Document project = new Document("$project",
                new Document("_id", 1)
                        .append("name", 1)
                        .append("full_name", 1)
                        .append("language", 1)
                        .append("category", 1)
                        .append("topics", 1)
                        .append("description", 1)
                        .append("stars_count", 1)
                        .append("license", 1)
                        .append("score", 1)
        );

        List<Document> pipeline = Arrays.asList(vectorSearch, addFields, match, sort, limit, project);

        List<ScoredProject> results = new ArrayList<>();
        try {
            for (Document doc : col.aggregate(pipeline)) {
                ScoredProject sp = new ScoredProject();
                sp.id = doc.getObjectId("_id") != null ? doc.getObjectId("_id").toHexString() : null;
                sp.name = doc.getString("name");
                sp.fullName = doc.getString("full_name");
                sp.language = doc.getString("language");
                sp.category = doc.getString("category");
                Object topics = doc.get("topics");
                if (topics instanceof List) sp.topics = (List<String>) topics;
                sp.description = doc.getString("description");
                Object stars = doc.get("stars_count");
                if (stars instanceof Number) sp.starsCount = ((Number) stars).intValue();
                sp.license = doc.getString("license");
                Object score = doc.get("score");
                if (score instanceof Number) sp.score = ((Number) score).doubleValue();
                results.add(sp);
            }
            log.info("[VectorSearch] 命中 {} 条 (minScore={})", results.size(), minScore);
        } catch (Exception e) {
            log.error("[VectorSearch] 执行失败: {} (请确认 Atlas 索引 '{}' 已创建并激活)",
                    e.getMessage(), vectorIndexName);
            log.error("[VectorSearch] 详细堆栈:", e);
        }

        return results;
    }

    // ========== 数据容器 ==========

    /** 带相似度分数的项目 —— 提供给 TigerRagService 组装回答 */
    @lombok.Data
    public static class ScoredProject {
        private String id;
        private String name;
        private String fullName;
        private String language;
        private String category;
        private List<String> topics;
        private String description;
        private Integer starsCount;
        private String license;
        /** 余弦相似度 0~1 */
        private Double score;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
