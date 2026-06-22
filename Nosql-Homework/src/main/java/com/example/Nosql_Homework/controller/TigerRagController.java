package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.entity.ChatMessage;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.impl.EmbeddingService;
import com.example.Nosql_Homework.service.impl.TigerRagService;
import com.example.Nosql_Homework.service.impl.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
    private final VectorSearchService vectorSearchService;

    // ========== 对话相关 ==========

    @PostMapping("/chat")
    public R<TigerRagService.RagAnswer> chat(@RequestBody ChatRequest req) {
        if (req == null || req.query == null || req.query.isBlank()) {
            return R.fail("query 不能为空");
        }
        try {
            TigerRagService.RagAnswer answer = tigerRagService.chat(req.sessionId, req.query.trim(), req.topK, req.minScore);
            if (answer == null || !answer.isSuccess()) {
                return R.fail(answer == null ? "未知错误" : answer.getErrorMsg());
            }
            return R.ok(answer);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[Tiger-RAG] /chat 接口未捕获异常: 异常类型={}, 消息={}, rootCause={}, 消息={}, sessionId={}, topK={}, minScore={}, query=\"{}\"",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    req.sessionId, req.topK, req.minScore,
                    req.query == null ? "" : req.query.length() > 120 ? req.query.substring(0, 120) + "..." : req.query, e);
            return R.fail("服务异常: " + root.getClass().getSimpleName() + " - " + root.getMessage());
        }
    }

    /**
     * 异步发起问答：立刻返回 taskId，前端通过 GET /chat/{taskId} 轮询结果。
     * 适合 LLM 耗时较长（>15秒）的场景，避免前端 axios 超时。
     */
    @PostMapping("/chat/start")
    public R<TigerRagService.ChatTask> startChat(@RequestBody ChatRequest req) {
        if (req == null || req.query == null || req.query.isBlank()) {
            return R.fail("query 不能为空");
        }
        try {
            TigerRagService.ChatTask task = tigerRagService.startChat(req.sessionId, req.query.trim(), req.topK, req.minScore);
            log.info("[Tiger-RAG] /chat/start -> taskId={}, query={}", task.getTaskId(), truncate(req.query, 80));
            return R.ok(task);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[Tiger-RAG] /chat/start 接口未捕获异常: 异常类型={}, 消息={}, rootCause={}, 消息={}, sessionId={}, query=\"{}\"",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    req.sessionId,
                    req.query == null ? "" : req.query.length() > 120 ? req.query.substring(0, 120) + "..." : req.query, e);
            return R.fail("服务异常: " + root.getClass().getSimpleName() + " - " + root.getMessage());
        }
    }

    /** 轮询查询任务状态与结果 */
    @GetMapping("/chat/{taskId}")
    public R<TigerRagService.ChatTask> getChatTask(@PathVariable String taskId) {
        if (taskId == null || taskId.isBlank()) return R.fail("taskId 不能为空");
        TigerRagService.ChatTask task = tigerRagService.getTaskResult(taskId);
        if (task == null) {
            return R.fail("任务不存在或已过期 (仅保留 10 分钟)");
        }
        return R.ok(task);
    }

    /**
     * SSE 流式问答：通过 Server-Sent Events 实时推送 LLM token。
     * 前端使用 EventSource 或 fetch 读取，无需轮询。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String query,
                                  @RequestParam(required = false) String sessionId,
                                  @RequestParam(required = false) Integer topK,
                                  @RequestParam(required = false) Double minScore) {
        if (query == null || query.isBlank()) {
            SseEmitter bad = new SseEmitter(0L);
            bad.completeWithError(new IllegalArgumentException("query 不能为空"));
            return bad;
        }

        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        // 关键：在独立线程中执行 streamChat，让 Controller 立即返回 emitter。
        // 否则 streamChat 的同步阻塞会导致所有 emitter.send() 在 emitter 返回前排队，
        // 最终被一次性 flush，前端看起来就不是流式输出。
        new Thread(() -> {
            tigerRagService.streamChat(sessionId, query.trim(), topK, minScore,
                    // onToken: 逐 token 推送
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    // onComplete: 推送最终结果 (refs, duration, knowledgeSource 等元数据)
                    answer -> {
                        try {
                            emitter.send(SseEmitter.event().name("done").data(answer));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    // onError: 推送错误
                    error -> {
                        try {
                            emitter.send(SseEmitter.event().name("error")
                                    .data(error.getMessage() != null ? error.getMessage() : "未知错误"));
                        } catch (IOException ignored) {
                        } finally {
                            emitter.completeWithError(error);
                        }
                    }
            );
        }, "tiger-rag-sse").start();

        return emitter;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
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
        try {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", "Tiger-RAG");
            info.put("description", "基于 MongoDB Atlas 向量检索 + 豆包大模型的 GitHub 项目智能问答系统");
            info.put("vectorCollection", "projects");
            info.put("vectorIndex", "tiger_projects_vector_index");
            info.put("embeddingModel", embeddingService.getModelName());
            info.put("totalProjects", projectRepo.count());
            info.put("projectsMissingEmbedding", projectRepo.countByHasEmbeddingFalseOrHasEmbeddingIsNull());
            // 同时把底层 "索引健康度" 一并返回，前端可以根据 ready=false 给出操作提示
            info.put("index", vectorSearchService.indexHealth());
            return R.ok(info);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[Tiger-RAG] /info 接口异常: 异常类型={}, 消息={}, rootCause={}, 消息={}",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(), e);
            return R.fail("获取信息失败: " + root.getMessage());
        }
    }

    /**
     * 轻量级诊断接口: 只返回 "当前向量库能否正常工作"，供前端在问答页 / 搜索页展示提示。
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(vectorSearchService.indexHealth());
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

        try {
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

                        p.setEmbedding(v);
                        p.setEmbeddingModel(embeddingService.getModelName());
                        p.setHasEmbedding(true);
                        projectRepo.save(p);
                        ok++;
                    }
                    processed += batch.size();
                    log.info("[Tiger-RAG] 进度 {}/{}, 当前批成功 {}", processed, totalMissing, ok);
                } catch (Exception e) {
                    Throwable root = e;
                    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                    log.error("[Tiger-RAG] batch-embed 批 {} 失败: 异常类型={}, 消息={}, rootCause={}, 消息={}, 批项目数={}",
                            i / batchSize + 1,
                            e.getClass().getSimpleName(), e.getMessage(),
                            root.getClass().getSimpleName(), root.getMessage(),
                            batch.size(), e);
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
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[Tiger-RAG] batch-embed 顶层异常: 异常类型={}, 消息={}, rootCause={}, 消息={}, 耗时={}ms",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    cost, e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", root.getClass().getSimpleName() + ": " + root.getMessage());
            result.put("costMs", cost);
            return R.fail("批量生成向量失败: " + root.getMessage());
        }
    }

    // ========== DTO ==========

    @lombok.Data
    public static class ChatRequest {
        private String sessionId;
        private String query;
        private Integer topK;
        private Double minScore;
    }
}
