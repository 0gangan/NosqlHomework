package com.example.Nosql_Homework.service.impl;

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
 * 1. 用户问题 -> 调用通义千问 DashScope Embedding API 生成 query vector
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

    @Value("${tiger.rag.top-k:20}")
    private int defaultTopK;

    @Value("${tiger.rag.num-candidates:500}")
    private int defaultNumCandidates;

    @Value("${tiger.rag.min-score:0.3}")
    private double defaultMinScore;

    /** 直接使用项目集合做向量检索 */
    private static final String COLLECTION = "projects";

    /** 有多少条项目数据可以被索引，低于这个数字时给出更醒目的警告 */
    private static final long WARN_MIN_HAS_EMBEDDING = 1;

    /**
     * 根据用户自然语言问题，在 projects 向量库中检索与之最相关的项目
     */
    public List<ScoredProject> searchByText(String query, Integer topK, Double minScore) {
        int k = topK != null ? topK : defaultTopK;
        double ms = minScore != null ? minScore : defaultMinScore;

        if (query == null || query.isBlank()) return new ArrayList<>();

        log.info("[VectorSearch] text={}, topK={}, minScore={}", truncate(query, 80), k, ms);

        // 在真正调用向量索引前，先检查一下集合里是否有项目带有 embedding 字段
        logIndexHealth(ms, k);

        long t0 = System.currentTimeMillis();
        List<Float> queryVector;
        try {
            queryVector = embeddingService.embed(query);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[VectorSearch] 生成 query embedding 失败: 异常类型={}, 消息={}, rootCause={}, 消息={}, 耗时={}ms, 查询=\"{}\"",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    System.currentTimeMillis() - t0, truncate(query, 80), e);
            return new ArrayList<>();
        }
        if (queryVector == null || queryVector.isEmpty()) {
            log.warn("[VectorSearch] embedding 返回空向量, 跳过检索, 耗时={}ms", System.currentTimeMillis() - t0);
            return new ArrayList<>();
        }
        log.info("[VectorSearch] query vector 生成成功, 维度={}, 耗时={}ms", queryVector.size(), System.currentTimeMillis() - t0);

        return doVectorSearch(queryVector, k, ms, defaultNumCandidates);
    }

    /**
     * 直接给已算好的向量做检索 (开放给测试/外部系统使用)
     */
    public List<ScoredProject> searchByVector(List<Float> queryVector, Integer topK, Double minScore) {
        if (queryVector == null || queryVector.isEmpty()) return new ArrayList<>();
        int k = topK != null ? topK : defaultTopK;
        double ms = minScore != null ? minScore : defaultMinScore;
        logIndexHealth(ms, k);
        return doVectorSearch(queryVector, k, ms, defaultNumCandidates);
    }

    /**
     * 给出一个 "当前向量库是否能正常工作" 的健康状况摘要（不抛异常，只记日志 + 返回可序列化对象）
     * 方便 /api/projects/embedding-stats 等接口直接暴露给前端 / 运维面板查看。
     */
    public Map<String, Object> indexHealth() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("indexName", vectorIndexName);
        info.put("collection", COLLECTION);
        info.put("embeddingModel", embeddingService.getModelName());
        info.put("topK", defaultTopK);
        info.put("numCandidates", defaultNumCandidates);
        info.put("minScore", defaultMinScore);
        try {
            long total = mongoTemplate.estimatedCount(com.example.Nosql_Homework.entity.Project.class);
            long hasEmbedding = mongoTemplate.count(
                    org.springframework.data.mongodb.core.query.Query.query(
                            new org.springframework.data.mongodb.core.query.Criteria()
                                    .andOperator(
                                            org.springframework.data.mongodb.core.query.Criteria.where("embedding").exists(true),
                                            org.springframework.data.mongodb.core.query.Criteria.where("embedding").ne(null)
                                    )),
                    com.example.Nosql_Homework.entity.Project.class);
            long hasEmbeddingFlag = mongoTemplate.count(
                    org.springframework.data.mongodb.core.query.Query.query(
                            org.springframework.data.mongodb.core.query.Criteria.where("hasEmbedding").is(true)),
                    com.example.Nosql_Homework.entity.Project.class);
            info.put("total", total);
            info.put("hasEmbeddingField", hasEmbedding);
            info.put("hasEmbeddingFlagTrue", hasEmbeddingFlag);
            info.put("ready", hasEmbedding >= WARN_MIN_HAS_EMBEDDING);
            if (hasEmbedding < WARN_MIN_HAS_EMBEDDING) {
                info.put("hint", "项目集合里几乎没有带 embedding 字段的文档，请先调用 POST /api/projects/batch-embed 生成向量，然后在 MongoDB Atlas 控制台针对 projects 集合创建 knnVector 索引 " + vectorIndexName);
            } else {
                info.put("hint", "ok");
            }
        } catch (Exception e) {
            info.put("error", e.getMessage());
            info.put("ready", false);
        }
        return info;
    }

    private void logIndexHealth(double minScore, int topK) {
        try {
            long total = mongoTemplate.estimatedCount(com.example.Nosql_Homework.entity.Project.class);
            long hasEmbedding = mongoTemplate.count(
                    org.springframework.data.mongodb.core.query.Query.query(
                            new org.springframework.data.mongodb.core.query.Criteria()
                                    .andOperator(
                                            org.springframework.data.mongodb.core.query.Criteria.where("embedding").exists(true),
                                            org.springframework.data.mongodb.core.query.Criteria.where("embedding").ne(null)
                                    )),
                    com.example.Nosql_Homework.entity.Project.class);
            log.info("[VectorSearch] 索引健康度: collection={}, index={}, totalProjects={}, withEmbedding={}, topK={}, minScore={}",
                    COLLECTION, vectorIndexName, total, hasEmbedding, topK, minScore);
            if (hasEmbedding < WARN_MIN_HAS_EMBEDDING) {
                log.warn("[VectorSearch] ⚠️ collection='{}' 中只有 {} 个项目有 embedding 字段（总共 {} 个项目），$vectorSearch 将一直命中 0 条；" +
                        "请先执行 POST /api/projects/batch-embed 批量生成向量，并在 MongoDB Atlas 控制台针对 projects 集合创建 knnVector 索引 '{}'",
                        COLLECTION, hasEmbedding, total, vectorIndexName);
            }
        } catch (Exception e) {
            log.warn("[VectorSearch] 读取索引健康度失败（不影响主流程）: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ========== 内部实现 ==========

    private List<ScoredProject> doVectorSearch(List<Float> queryVector, int topK, double minScore, int numCandidates) {
        MongoDatabase db = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase();
        MongoCollection<Document> col = db.getCollection(COLLECTION);

        // List<Float> -> List<Double>（MongoDB 驱动对 float 也支持，但 double 更通用）
        List<Double> vec = new ArrayList<>(queryVector.size());
        for (Float f : queryVector) vec.add(f.doubleValue());

        // 让 Atlas 返回 topK * 3 个候选（最小 30），然后由 $match 筛选后再 limit topK
        // 这样即使 $match 砍掉一半，仍能稳定拿到 topK 个结果
        int atlasLimit = Math.max(topK * 3, 30);

        Document vectorSearch = new Document("$vectorSearch",
                new Document("index", vectorIndexName)
                        .append("path", "embedding")
                        .append("queryVector", vec)
                        .append("numCandidates", numCandidates)
                        .append("limit", atlasLimit)
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
            log.info("[VectorSearch] 命中 {} 条 (atlasLimit={}, minScore={}, 有效召回率={}/{})",
                    results.size(), atlasLimit, minScore, results.size(), topK);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[VectorSearch] 执行 $vectorSearch 失败: 异常类型={}, 消息={}, rootCause={}, 消息={}, 索引={}, 集合={}, topK={}, minScore={}, numCandidates={}",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    vectorIndexName, COLLECTION, topK, minScore, numCandidates, e);
        }

        return results;
    }

    // ========== 结构化查询 ==========

    /**
     * 按指定字段排序查询（例如 Star 最多的项目）。
     * sortField: starsCount / forksCount / openIssues / createdAt / pushedAt / name
     */
    public List<ScoredProject> querySorted(String sortField, boolean desc, int limit) {
        String mongoField = sortFieldToMongo(sortField);
        if (mongoField == null) return new ArrayList<>();

        Document sortDoc = new Document(mongoField, desc ? -1 : 1);
        Document projectDoc = buildProjection();

        try {
            MongoDatabase db = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase();
            MongoCollection<Document> col = db.getCollection(COLLECTION);
            List<ScoredProject> results = new ArrayList<>();
            for (Document doc : col.find().sort(sortDoc).limit(limit).projection(projectDoc)) {
                ScoredProject sp = docToScored(doc);
                sp.score = 1.0;
                results.add(sp);
            }
            log.info("[VectorSearch] 结构化排序: sortField={} desc={} limit={} 返回{}条", sortField, desc, limit, results.size());
            return results;
        } catch (Exception e) {
            log.error("[VectorSearch] 结构化排序失败: {} {}", sortField, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 按条件过滤查询（例如"Java 语言的项目"）。
     * filterField: language / license / category
     */
    public List<ScoredProject> queryFiltered(String filterField, String filterValue,
                                              String sortField, boolean desc, int limit) {
        String mongoFilter = fieldToMongo(filterField);
        String mongoSort = sortFieldToMongo(sortField);
        if (mongoFilter == null) return new ArrayList<>();

        // language 用 ^$ 锚定精确匹配 + 忽略大小写，避免 "Java" 误匹配 "JavaScript"，同时兼容 "java"/"JAVA"
        Document filterDoc;
        if ("language".equals(filterField)) {
            filterDoc = new Document(mongoFilter, new Document("$regex", "^" + filterValue + "$").append("$options", "i"));
        } else {
            filterDoc = new Document(mongoFilter, new Document("$regex", filterValue).append("$options", "i"));
        }
        Document sortDoc = mongoSort != null ? new Document(mongoSort, desc ? -1 : 1) : new Document("stars_count", -1);
        Document projectDoc = buildProjection();

        try {
            MongoDatabase db = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase();
            MongoCollection<Document> col = db.getCollection(COLLECTION);
            List<ScoredProject> results = new ArrayList<>();
            for (Document doc : col.find(filterDoc).sort(sortDoc).limit(limit).projection(projectDoc)) {
                ScoredProject sp = docToScored(doc);
                sp.score = 1.0;
                results.add(sp);
            }
            log.info("[VectorSearch] 结构化过滤: {}={} sort={} limit={} 返回{}条",
                    filterField, filterValue, sortField, limit, results.size());
            return results;
        } catch (Exception e) {
            log.error("[VectorSearch] 结构化过滤失败: {} {} {}", filterField, filterValue, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ========== 字段映射 ==========

    private static String sortFieldToMongo(String field) {
        if (field == null) return null;
        return switch (field) {
            case "stars_count" -> "stars_count";
            case "forks_count" -> "forks_count";
            case "open_issues" -> "open_issues";
            case "created_at" -> "created_at";
            case "pushed_at" -> "pushed_at";
            case "updated_at" -> "updated_at";
            case "name" -> "name";
            default -> null;
        };
    }

    private static String fieldToMongo(String field) {
        if (field == null) return null;
        return switch (field) {
            case "language" -> "language";
            case "license" -> "license";
            case "category" -> "category";
            default -> null;
        };
    }

    private Document buildProjection() {
        return new Document("_id", 1)
                .append("name", 1)
                .append("full_name", 1)
                .append("language", 1)
                .append("category", 1)
                .append("topics", 1)
                .append("description", 1)
                .append("stars_count", 1)
                .append("forks_count", 1)
                .append("license", 1);
    }

    private ScoredProject docToScored(Document doc) {
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
        return sp;
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
