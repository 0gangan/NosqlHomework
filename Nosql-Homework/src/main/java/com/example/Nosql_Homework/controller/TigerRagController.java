package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.entity.ChatMessage;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.EmbeddingService;
import com.example.Nosql_Homework.service.TigerRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Tiger-RAG 智能问答接口
 *
 * 说明：
 *   - 向量数据库直接使用 MongoDB 的 projects 集合
 *   - 每个 project 会预先生成 1024 维 embedding（调用 /api/tiger-rag/projects/batch-embed 触发）
 *   - MongoDB Atlas 控制台需要创建名为 tiger_projects_vector_index 的向量搜索索引
 *       字段: embedding  knnVector, dimensions 1024, similarity cosine
 */
@Slf4j
@RestController
@RequestMapping("/api/tiger-rag")
@RequiredArgsConstructor
public class TigerRagController {

    private final TigerRagService tigerRagService;
    private final ProjectRepository projectRepo;
    private final EmbeddingService embeddingService;
    private final MongoTemplate mongoTemplate;

    // ========== 对话相关 ==========

    @PostMapping("/chat")
    public R<TigerRagService.RagAnswer> chat(@RequestBody ChatRequest req) {
        if (req == null || req.query == null || req.query.isBlank()) {
            return R.fail("query 不能为空");
        }
        TigerRagService.RagAnswer answer = tigerRagService.chat(req.sessionId, req.query.trim());
        if (answer == null || !answer.isSuccess()) {
            return R.fail(answer == null ? "未知错误" : answer.getErrorMsg());
        }
        return R.ok(answer);
    }

    @GetMapping("/history")
    public R<List<ChatMessage>> history(@RequestParam String sessionId) {
        return R.ok(tigerRagService.listHistory(sessionId));
    }

    @DeleteMapping("/session")
    public R<Void> clearSession(@RequestParam String sessionId) {
        tigerRagService.clearSession(sessionId);
        return R.ok();
    }

    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Tiger-RAG");
        info.put("description", "基于 MongoDB Atlas 向量检索 + 豆包大模型的 GitHub 项目智能问答系统");
        info.put("vectorCollection", "projects");
        info.put("vectorIndex", "tiger_projects_vector_index");
        info.put("embeddingModel", embeddingService.getModelName());
        info.put("totalProjects", projectRepo.count());
        info.put("projectsMissingEmbedding", projectRepo.countByHasEmbeddingFalseOrHasEmbeddingIsNull());
        return R.ok(info);
    }

    // ========== 向量库管理 ==========

    /**
     * 给项目集合中尚未生成向量的项目批量生成 embedding。
     * 建议在首次部署、或新增了一批项目后调用一次。
     */
    @PostMapping("/projects/batch-embed")
    public R<Map<String, Object>> batchEmbedProjects(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(required = false) Boolean force
    ) {
        log.info("[Tiger-RAG] 开始为 projects 批量生成向量, batchSize={}, force={}", batchSize, force);
        long start = System.currentTimeMillis();

        List<Project> missing;
        if (Boolean.TRUE.equals(force)) {
            missing = projectRepo.findAll();
        } else {
            missing = projectRepo.findByHasEmbeddingFalseOrHasEmbeddingIsNull();
        }
        if (missing.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("generated", 0);
            result.put("total", projectRepo.count());
            result.put("remaining", 0);
            result.put("message", "没有需要生成向量的项目");
            result.put("costMs", 0);
            return R.ok(result);
        }

        // 分批生成
        int totalMissing = missing.size();
        int processed = 0;
        int ok = 0;

        for (int i = 0; i < totalMissing; i += batchSize) {
            int end = Math.min(i + batchSize, totalMissing);
            List<Project> batch = missing.subList(i, end);

            // 构造每个项目的 embedding 文本
            List<String> texts = new ArrayList<>(batch.size());
            for (Project p : batch) texts.add(p.toEmbeddingText());

            try {
                List<List<Float>> vectors = embeddingService.embedBatch(texts);
                for (int j = 0; j < batch.size(); j++) {
                    Project p = batch.get(j);
                    List<Float> v = j < vectors.size() ? vectors.get(j) : null;
                    if (v == null || v.isEmpty()) continue;

                    Update update = new Update()
                            .set("embedding", v)
                            .set("embedding_model", embeddingService.getModelName())
                            .set("has_embedding", true);
                    mongoTemplate.updateFirst(Query.query(where("_id").is(p.getId())), update, Project.class);
                    ok++;
                }
                processed += batch.size();
                log.info("[Tiger-RAG] 进度 {}/{}, 当前批成功 {}", processed, totalMissing, ok);
            } catch (Exception e) {
                log.error("[Tiger-RAG] 批 {} 生成向量失败: {}", i / batchSize, e.getMessage(), e);
            }
        }

        long cost = System.currentTimeMillis() - start;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", ok);
        result.put("total", projectRepo.count());
        result.put("remaining", projectRepo.countByHasEmbeddingFalseOrHasEmbeddingIsNull());
        result.put("costMs", cost);
        log.info("[Tiger-RAG] 向量补全完成, 成功 {}, 耗时 {} ms", ok, cost);
        return R.ok(result);
    }

    // ========== DTO ==========

    @lombok.Data
    public static class ChatRequest {
        private String sessionId;
        private String query;
    }
}
