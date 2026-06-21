package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.entity.ChatMessage;
import com.example.Nosql_Homework.repository.ChatMessageRepository;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Tiger-RAG 智能问答核心服务
 *
 * 流程:
 *   用户问题 --(1)--> 向量检索 (MongoDB Atlas $vectorSearch, 针对 projects 集合)
 *                  \
 *                   -(2)--> 组装 Prompt (系统角色 + 检索到的项目片段 + 历史对话)
 *                  \
 *                   -(3)--> 调用大模型 (豆包 / 火山方舟)
 *                  \
 *                   -(4)--> 保存问答对到 chat_messages
 *                  \
 *                   -(5)--> 返回回答 + 引用的项目给前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TigerRagService {

    private final VectorSearchService vectorSearchService;
    private final ChatMessageRepository chatMessageRepo;
    private final OpenAiChatModel chatModel;

    /** 任务结果存储：taskId -> ChatTask (仅在内存中，重启即清空) */
    private static final java.util.Map<String, ChatTask> TASK_STORE = new java.util.concurrent.ConcurrentHashMap<>();
    /** 任务结果保留时间：10 分钟内可以再次查询 */
    private static final long TASK_TTL_MS = 10 * 60 * 1000L;

    /** 异步任务的状态：processing / completed / failed / expired */
    @lombok.Data
    public static class ChatTask {
        private String taskId;
        private String sessionId;
        private String query;
        private String status;     // "processing" | "completed" | "failed" | "expired"
        private RagAnswer answer;  // 最终结果
        private String errorMsg;
        private long createdAt;    // 任务创建时间
        private long finishedAt;   // 任务完成时间
    }

    /** RAG 模式提示词：有知识库数据时使用，可做项目查询 + 分析预测 */
    @Value("${tiger.rag.system-prompt:你是一个叫 Tiger 的智能技术分析师，擅长结合 GitHub 开源项目数据和你的技术知识为用户提供专业见解。\\n你可以：\\n- 查询 GitHub 项目（描述、Star 数、作者、链接、标签、语言、协议、创建时间、Fork 数）；\\n- 做技术对比、项目评估、推荐项目；\\n- 做行业趋势分析（如编程语言热度、框架走势、生态变化等）；\\n- 做预测和展望（基于现有数据进行合理推断）。\\n请严格遵守：\\n1. 当有项目数据时，涉及该项目的具体信息必须严格从中提取，不得编造；\\n2. 做趋势分析、预测、对比、排名时，如有项目数据可作为佐证则引用，无匹配数据时使用你训练期间积累的行业常识；\\n3. 保持回答条理清晰、有数据支撑的地方请给出数据。}")
    private String systemPrompt;

    /** 降级模式提示词：知识库无匹配数据时使用，允许 LLM 依赖自身训练数据做分析类回答 */
    @Value("${tiger.rag.system-prompt-fallback:你是一个叫 Tiger 的智能技术分析师，可以回答用户的各类问题。\\n项目库中没有找到与该问题匹配的项目数据，请基于你的训练数据和你训练期间积累的行业知识、常识来回答。\\n你可以做：技术分析、趋势预测、语言对比、项目推荐、市场评估、历史回顾等各类分析类问题。\\n回答要求：\\n1. 不要编造不存在的 GitHub 项目名称、作者、Star 数或链接；\\n2. 回答开头可以简短说明「项目库中暂无匹配项目，以下基于通用知识和行业经验为你回答」；\\n3. 回答条理清晰、结构分明，必要时使用列表、粗体。}")
    private String systemPromptFallback;

    @Value("${tiger.rag.top-k:15}")
    private int topK;

    @Value("${tiger.rag.min-score:0.35}")
    private double minScore;

    /** 历史对话最多纳入几轮 */
    private static final int HISTORY_ROUNDS = 6;

    /**
     * 发起一次 RAG 问答（sessionId 可为空，为空则不保存历史）。
     * topK / minScore 若传入 null，则使用 application.properties 中的默认值。
     */
    public RagAnswer chat(String sessionId, String query, Integer reqTopK, Double reqMinScore) {
        long start = System.currentTimeMillis();

        if (query == null || query.isBlank()) {
            return RagAnswer.fail("问题不能为空");
        }

        final int k = reqTopK != null && reqTopK > 0 ? reqTopK : topK;
        final double ms = reqMinScore != null && reqMinScore >= 0 && reqMinScore <= 1 ? reqMinScore : minScore;

        log.info("[Tiger-RAG] ========== 开始回答 ==========");
        log.info("[Tiger-RAG] session={}, 问题: {}, topK={}, minScore={}", sessionId, truncate(query, 120), k, ms);

        // ---------- (0.5) 问题路由：先识别是否为结构化查询 ----------
        QueryIntent intent = extractQueryIntent(query);

        // ---------- (1) 数据检索 (按意图分派) ----------
        List<VectorSearchService.ScoredProject> refs;
        String searchMethod = "vector";
        long vsStart = System.currentTimeMillis();
        try {
            if (intent != null && intent.isStructured()) {
                if (intent.filterField != null) {
                    // 过滤 + 排序 (如 "Java项目 Star最多")
                    refs = vectorSearchService.queryFiltered(
                            intent.filterField, intent.filterValue,
                            intent.sortField, intent.sortDesc, k);
                    searchMethod = "filter";
                } else {
                    // 纯排序 (如 "Star最多")
                    refs = vectorSearchService.querySorted(intent.sortField, intent.sortDesc, k);
                    searchMethod = "rank";
                }
            } else {
                // 默认向量检索
                refs = vectorSearchService.searchByText(query, k, ms);
            }
            log.info("[Tiger-RAG] 命中 {} 个项目 (模式={}, 耗时 {}ms)", refs.size(), searchMethod, System.currentTimeMillis() - vsStart);
            if (refs.isEmpty() && "vector".equals(searchMethod)) {
                Map<String, Object> health = vectorSearchService.indexHealth();
                log.warn("[Tiger-RAG] ⚠️ 未命中任何项目。当前索引状态: {}", health);
            }
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - vsStart;
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[Tiger-RAG] 向量检索失败: 异常类型={}, 消息={}, rootCause={}, 消息={}, topK={}, minScore={}, 耗时={}ms",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    k, ms, cost, e);
            refs = new ArrayList<>();
        }

        // 计算检索结果最高相似度 score
        // 注意：结构化查询（rank/filter）的 score 恒为 1.0，此时强相关判定不再基于 score
        double highestScore = 0.0;
        if (refs != null && !refs.isEmpty()) {
            for (VectorSearchService.ScoredProject r : refs) {
                if (r.getScore() != null && r.getScore() > highestScore) highestScore = r.getScore();
            }
        }
        boolean isStructured = !"vector".equals(searchMethod);
        // 结构化查询 → 直接当强相关处理（因为是精确查数据库）；向量查询 → 按 score 分档
        boolean strongRefs = isStructured || (!refs.isEmpty() && highestScore >= 0.55);
        boolean weakRefs = !isStructured && !refs.isEmpty() && highestScore >= 0.3 && highestScore < 0.55;
        boolean hasKnowledge = strongRefs;

        // ---------- (2) 组装 Prompt ----------
        String fullPrompt = buildPrompt(sessionId, query, refs, highestScore, strongRefs, weakRefs, isStructured);
        log.debug("[Tiger-RAG] Prompt 长度: {} chars, highestScore={}, strongRefs={}, weakRefs={}",
                fullPrompt.length(), highestScore, strongRefs, weakRefs);

        // ---------- (3) 调用大模型 ----------
        String answer;
        long llmStart = System.currentTimeMillis();
        try {
            answer = chatModel.generate(fullPrompt);
            log.info("[Tiger-RAG] 大模型返回，耗时 {} ms, 回答长度: {} chars",
                    System.currentTimeMillis() - llmStart, answer == null ? 0 : answer.length());
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - llmStart;
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[Tiger-RAG] 大模型调用失败: 异常类型={}, 消息={}, rootCause={}, 消息={}, Prompt长度={} chars, 耗时={}ms",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    fullPrompt.length(), cost, e);
            // === 降级回复：LLM 不可用时，如向量搜索找到了项目，直接返回项目列表 ===
            if (refs != null && !refs.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("抱歉，智能模型暂时无法使用。不过我为你找到了以下 ").append(refs.size()).append(" 个相关 GitHub 项目，希望对你有帮助：\n\n");
                for (int ii = 0; ii < refs.size(); ii++) {
                    VectorSearchService.ScoredProject r = refs.get(ii);
                    sb.append("[").append(ii + 1).append("] ").append(r.getFullName() != null ? r.getFullName() : "Unknown").append("\n");
                    if (r.getLanguage() != null) sb.append("  - 编程语言: ").append(r.getLanguage()).append("\n");
                    if (r.getStarsCount() != null) sb.append("  - Stars: ").append(r.getStarsCount()).append("\n");
                    if (r.getDescription() != null) sb.append("  - 描述: ").append(truncate(r.getDescription(), 150)).append("\n");
                    sb.append("\n");
                }
                sb.append("---\n错误信息: ").append(root.getClass().getSimpleName()).append(" - ").append(root.getMessage()).append("\n");
                answer = sb.toString();
                log.warn("[Tiger-RAG] 使用降级回复（LLM 不可用，但有 {} 个项目结果）", refs.size());
            } else {
                answer = "抱歉，智能模型暂时无法使用。错误信息: " + root.getClass().getSimpleName() + " - " + root.getMessage() + "\n\n建议: 1) 检查 API Key 是否正确 2) 确认网络连接正常 3) 稍后重试";
                log.warn("[Tiger-RAG] LLM 不可用，无法生成回答（知识库 {} 个项目）", refs.size());
            }
        }

        long totalMs = System.currentTimeMillis() - start;

        // ---------- (4) 保存问答历史 ----------
        if (sessionId != null && !sessionId.isBlank()) {
            ChatMessage userMsg = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role("user")
                    .content(query)
                    .createdAt(new Date())
                    .build();
            chatMessageRepo.save(userMsg);

            List<ChatMessage.RefDocPreview> previews = new ArrayList<>();
            for (VectorSearchService.ScoredProject r : refs) {
                previews.add(ChatMessage.RefDocPreview.builder()
                        .docId(r.getId())
                        .title(r.getFullName())
                        .category(r.getLanguage())
                        .score(r.getScore())
                        .snippet(r.getDescription() == null ? "" :
                                r.getDescription().substring(0, Math.min(200, r.getDescription().length())))
                        .build());
            }
            ChatMessage assistantMsg = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .content(answer)
                    .refDocIds(refs.stream().map(VectorSearchService.ScoredProject::getId).toList())
                    .refDocsPreview(previews)
                    .createdAt(new Date())
                    .durationMs(totalMs)
                    .build();
            chatMessageRepo.save(assistantMsg);
        }

        log.info("[Tiger-RAG] ========== 回答完成 (总耗时 {} ms) ==========", totalMs);

        // ---------- (5) 返回 ----------
        RagAnswer result = new RagAnswer();
        result.answer = answer;
        result.sessionId = sessionId;
        result.refs = refs.stream().map(r -> {
            RagRef rr = new RagRef();
            rr.docId = r.getId();
            rr.title = r.getFullName();
            rr.category = r.getLanguage();
            rr.topics = r.getTopics();
            rr.license = r.getLicense();
            rr.score = r.getScore();
            rr.description = r.getDescription();
            rr.starsCount = r.getStarsCount();
            if (r.getFullName() != null) {
                rr.url = "https://github.com/" + r.getFullName();
                int slash = r.getFullName().indexOf('/');
                if (slash > 0) rr.author = r.getFullName().substring(0, slash);
            }
            return rr;
        }).toList();
        result.durationMs = totalMs;
        result.usedKnowledge = hasKnowledge;
        result.highestScore = highestScore;
        // rag = 强相关项目；hybrid = 有弱相关项目；llm = 无相关项目
        // rag = 强相关项目；structured = 精确查询；hybrid = 弱相关项目；llm = 无匹配
        if (isStructured) result.knowledgeSource = "structured";
        else if (strongRefs) result.knowledgeSource = "rag";
        else if (weakRefs) result.knowledgeSource = "hybrid";
        else result.knowledgeSource = "llm";
        return result;
    }

    /** 旧签名兼容：调用新签名走默认 topK/minScore */
    public RagAnswer chat(String sessionId, String query) {
        return chat(sessionId, query, null, null);
    }

    /**
     * 异步发起一次问答：立刻返回 taskId，真正执行在后台线程里完成。
     * 前端用 tigerRagGetTask(taskId) 轮询查询结果。
     */
    public ChatTask startChat(String sessionId, String query, Integer reqTopK, Double reqMinScore) {
        String taskId = "task_" + System.currentTimeMillis() + "_" + Math.abs((query == null ? "" : query).hashCode()) % 10000;
        ChatTask task = new ChatTask();
        task.setTaskId(taskId);
        task.setSessionId(sessionId);
        task.setQuery(query);
        task.setStatus("processing");
        task.setCreatedAt(System.currentTimeMillis());
        TASK_STORE.put(taskId, task);

        // 后台线程执行问答逻辑（注意：外部的异常要捕获，保证 task 永远能设置最终状态）
        new Thread(() -> {
            try {
                RagAnswer answer = chat(sessionId, query, reqTopK, reqMinScore);
                task.setAnswer(answer);
                if (answer != null && !answer.isSuccess()) {
                    task.setStatus("failed");
                    task.setErrorMsg(answer.getErrorMsg());
                } else {
                    task.setStatus("completed");
                }
            } catch (Exception e) {
                task.setStatus("failed");
                task.setErrorMsg("后台执行异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                log.error("[Tiger-RAG] 异步任务 {} 失败: {} - {}", taskId, e.getClass().getSimpleName(), e.getMessage(), e);
            } finally {
                task.setFinishedAt(System.currentTimeMillis());
                TASK_STORE.put(taskId, task);
                // 过期清理：顺便删掉 TTL 以外的旧任务（简单 O(n)）
                cleanExpiredTasks();
            }
        }, "tiger-rag-chat-" + taskId).start();

        return task;
    }

    /** 根据 taskId 查询异步任务结果。找不到或已过期返回 null */
    public ChatTask getTaskResult(String taskId) {
        if (taskId == null) return null;
        ChatTask task = TASK_STORE.get(taskId);
        if (task == null) return null;
        // 已超过 TTL，直接返回 expired 状态
        long age = System.currentTimeMillis() - task.getCreatedAt();
        if (age > TASK_TTL_MS) {
            task.setStatus("expired");
            return task;
        }
        return task;
    }

    /** 清理超过 TTL 的任务，避免内存泄漏（无需特别精确） */
    private void cleanExpiredTasks() {
        try {
            long now = System.currentTimeMillis();
            TASK_STORE.entrySet().removeIf(entry -> {
                ChatTask t = entry.getValue();
                long ended = t.getFinishedAt() > 0 ? t.getFinishedAt() : t.getCreatedAt();
                return now - ended > TASK_TTL_MS;
            });
        } catch (Exception ignored) {}
    }

    public List<ChatMessage> listHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return new ArrayList<>();
        return chatMessageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        chatMessageRepo.deleteBySessionId(sessionId);
    }

    // ============== private ==============

    /**
     * 组装给大模型的完整 Prompt。
     * 四模式：
     *   结构化模式 (isStructured)：结果来自数据库精确排序/过滤
     *   RAG 模式 (strongRefs)：有强相关向量检索结果
     *   混合模式 (weakRefs)：有弱相关向量检索结果
     *   LLM 模式 (其他)：项目库无匹配
     */
    private String buildPrompt(String sessionId, String query, List<VectorSearchService.ScoredProject> refs,
                               double highestScore, boolean strongRefs, boolean weakRefs, boolean isStructured) {
        StringBuilder sb = new StringBuilder();

        // ===== 模式 S：结构化查询（精确排序/过滤） =====
        if (isStructured && refs != null && !refs.isEmpty()) {
            sb.append(systemPrompt).append("\n\n");
            sb.append("--- 当前为「精确查询模式」(数据库直接排序/过滤) ---\n");
            sb.append("回答规则:\n");
            sb.append("1. 下面项目是数据库按指定条件精确返回的结果；\n");
            sb.append("2. 请基于这些项目回答，如果用户问了排序/筛选问题，直接按列表列出即可；\n");
            sb.append("3. 涉及项目的 作者/Star数/链接/标签/描述 必须与下面严格一致；\n");
            sb.append("4. 如果问题超出项目数据范围（如趋势分析），可结合通用知识补充。\n\n");

            sb.append("【精确查询结果】(共 ").append(refs.size()).append(" 个，非语义匹配)\n\n");
            for (int i = 0; i < refs.size(); i++) {
                VectorSearchService.ScoredProject r = refs.get(i);
                sb.append(String.format("--- 项目 %d ---\n", i + 1));
                if (r.getFullName() != null) {
                    sb.append("项目仓库: ").append(r.getFullName()).append("\n");
                    sb.append("项目链接: https://github.com/").append(r.getFullName()).append("\n");
                    int slash = r.getFullName().indexOf('/');
                    if (slash > 0) sb.append("作者: ").append(r.getFullName().substring(0, slash)).append("\n");
                }
                if (r.getName() != null) sb.append("项目名称: ").append(r.getName()).append("\n");
                if (r.getLanguage() != null) sb.append("编程语言: ").append(r.getLanguage()).append("\n");
                if (r.getCategory() != null) sb.append("分类: ").append(r.getCategory()).append("\n");
                if (r.getTopics() != null && !r.getTopics().isEmpty())
                    sb.append("标签: ").append(String.join(", ", r.getTopics())).append("\n");
                if (r.getStarsCount() != null) sb.append("Star 数量: ").append(r.getStarsCount()).append("\n");
                if (r.getLicense() != null) sb.append("开源协议: ").append(r.getLicense()).append("\n");
                if (r.getDescription() != null) sb.append("项目描述: ").append(r.getDescription()).append("\n");
                sb.append("\n");
            }
        }
        // ===== 模式 R：RAG 强相关 =====
        else if (strongRefs) {
            sb.append(systemPrompt).append("\n\n");
            sb.append("--- 当前为「项目知识库模式」(最高相似度 ").append(String.format("%.2f", highestScore)).append(") ---\n");
            sb.append("回答规则:\n");
            sb.append("1. 主要基于下面【参考项目】中的信息回答，不要编造任何不存在的项目、作者、Star 数或链接；\n");
            sb.append("2. 如果用户的问题属于预测类、趋势类、主观类（如『预测明年趋势』），可以结合你的通用知识做分析，但涉及具体项目数据时只以【参考项目】为准；\n");
            sb.append("3. 不要在回答中显式提到「向量数据库」「embedding」「最高相似度」等实现细节；\n");
            sb.append("4. 回答简洁、准确；必要时可以用列表、粗体等 Markdown 语法。\n\n");

            sb.append("【参考项目】(按相关度从高到低)\n");
            sb.append("注意：你回答中提到的任何「作者 / Star 数 / 链接 / 标签 / 描述」必须严格与下面项目信息一致，否则不要提及。\n\n");
            for (int i = 0; i < refs.size(); i++) {
                VectorSearchService.ScoredProject r = refs.get(i);
                sb.append(String.format("--- 项目 %d (相似度 %.2f) ---\n", i + 1, r.getScore() == null ? 0.0 : r.getScore()));
                if (r.getFullName() != null) {
                    sb.append("项目仓库: ").append(r.getFullName()).append("\n");
                    sb.append("项目链接: https://github.com/").append(r.getFullName()).append("\n");
                    int slash = r.getFullName().indexOf('/');
                    if (slash > 0) sb.append("作者: ").append(r.getFullName().substring(0, slash)).append(" (owner / author)\n");
                }
                if (r.getName() != null) sb.append("项目名称: ").append(r.getName()).append("\n");
                if (r.getLanguage() != null) sb.append("编程语言: ").append(r.getLanguage()).append("\n");
                if (r.getCategory() != null) sb.append("分类: ").append(r.getCategory()).append("\n");
                if (r.getTopics() != null && !r.getTopics().isEmpty())
                    sb.append("标签: ").append(String.join(", ", r.getTopics())).append("\n");
                if (r.getStarsCount() != null) sb.append("Star 数量: ").append(r.getStarsCount()).append("\n");
                if (r.getLicense() != null) sb.append("开源协议: ").append(r.getLicense()).append("\n");
                if (r.getDescription() != null) sb.append("项目描述: ").append(r.getDescription()).append("\n");
                sb.append("\n");
            }
        }
        // ===== 模式 B：混合模式（弱相关） =====
        else if (weakRefs) {
            sb.append(systemPromptFallback).append("\n\n");
            sb.append("--- 当前为「混合模式」--\n");
            sb.append("当前知识库中只有 ").append(refs.size()).append(" 个与问题弱相关的项目 (最高相似度 ").append(String.format("%.2f", highestScore)).append(")。\n");
            sb.append("回答规则:\n");
            sb.append("1. 以你的通用知识为主来回答用户问题（例如趋势预测、市场分析、技术走向等）；\n");
            sb.append("2. 下面【参考项目】仅作为补充材料，可选择性提及，但不要把它们当做预测依据来推导结论；\n");
            sb.append("3. 提到具体项目时，其作者/Star数/链接/标签/描述必须与下面信息一致，否则不要提及；\n");
            sb.append("4. 回答简洁、有条理，可以用列表、粗体等 Markdown 语法。\n\n");

            sb.append("【参考项目】(按相关度从高到低，仅作补充)\n\n");
            for (int i = 0; i < refs.size(); i++) {
                VectorSearchService.ScoredProject r = refs.get(i);
                sb.append(String.format("--- 项目 %d (相似度 %.2f) ---\n", i + 1, r.getScore() == null ? 0.0 : r.getScore()));
                if (r.getFullName() != null) {
                    sb.append("项目仓库: ").append(r.getFullName()).append("\n");
                    sb.append("项目链接: https://github.com/").append(r.getFullName()).append("\n");
                }
                if (r.getName() != null) sb.append("项目名称: ").append(r.getName()).append("\n");
                if (r.getLanguage() != null) sb.append("编程语言: ").append(r.getLanguage()).append("\n");
                if (r.getStarsCount() != null) sb.append("Star 数量: ").append(r.getStarsCount()).append("\n");
                if (r.getDescription() != null) sb.append("项目描述: ").append(r.getDescription()).append("\n");
                sb.append("\n");
            }
        }
        // ===== 模式 C：LLM 自身知识 =====
        else {
            sb.append(systemPromptFallback).append("\n\n");
            sb.append("--- 当前为「通用知识模式」---\n");
            sb.append("项目库中未找到与该问题直接相关的项目。请完全基于你的训练数据和通用知识来回答。\n\n");
        }

        // ---- 历史对话 (三种模式共用) ----
        if (sessionId != null && !sessionId.isBlank()) {
            List<ChatMessage> history = chatMessageRepo
                    .findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, HISTORY_ROUNDS * 2))
                    .getContent();
            history = new ArrayList<>(history);
            Collections.reverse(history);
            if (!history.isEmpty()) {
                sb.append("【历史对话】\n");
                for (ChatMessage m : history) {
                    String role = "user".equals(m.getRole()) ? "用户" : "助手";
                    sb.append(role).append(": ").append(truncate(m.getContent(), 300)).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("【当前问题】\n").append(query).append("\n\n");
        sb.append("请给出你的回答：");
        return sb.toString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    // ========== 问题意图路由器 ==========

    /**
     * 从用户问题中提取结构化查询意图。
     * 用关键字/规则匹配而不是调 LLM，零延迟。
     *
     * 识别规则：
     *   - "star/tar/fork 最多/最高/最大的/前N"    →  排序查询
     *   - "最老/最早"                            →  按 created_at 升序
     *   - "最新/最近"                            →  按 pushed_at 降序
     *   - "Java/C/Python/Go/... 项目"           →  过滤查询
     *   - "MIT/GPL/Apache/... 协议的项目"        →  过滤查询
     */
    private QueryIntent extractQueryIntent(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();

        QueryIntent intent = new QueryIntent();

        // --- 排序意图 ---
        if (containsAny(q, "star", "tar", "星", "关注", "最火", "热门", "受欢迎", "最多人",
                "fork", "forks", "复刻", "分叉", "克隆数", "fork数",
                "最早", "最老", "最久", "第一次", "最先",
                "最新", "最近", "刚", "最后更新", "活跃")) {

            if (containsAny(q, "最早", "最老", "最久", "第一次", "最先")) {
                intent.sortField = "created_at";
                intent.sortDesc = false;
            } else if (containsAny(q, "最新", "最近", "刚", "最后更新", "活跃")) {
                intent.sortField = "pushed_at";
                intent.sortDesc = true;
            } else {
                intent.sortField = "stars_count";
                intent.sortDesc = true;
            }
        }

        // --- 过滤意图（提取语言/协议名） ---
        String[] langPatterns = {"Java", "Python", "JavaScript", "TypeScript", "Go", "Rust",
                "C++", "C\\#", "C语言", "Ruby", "PHP", "Swift", "Kotlin", "Scala", "R语言"};
        for (String p : langPatterns) {
            if (q.contains(p) && (q.contains("语言") || q.contains("项目") || q.contains("代码") || q.contains("开发"))) {
                intent.filterField = "language";
                intent.filterValue = p.replace("\\#", "#").replace("R语言", "R");
                break;
            }
        }
        // 也支持简单写"Java项目"、"Python库"等
        if (intent.filterField == null) {
            String[] simpleLangs = {"Java", "Python", "JavaScript", "TypeScript", "Go", "Rust",
                    "C++", "C#", "Ruby", "PHP", "Swift", "Kotlin", "Scala"};
            for (String l : simpleLangs) {
                if (q.contains(l)) {
                    // 确认是编程语言而不是普通词汇（后面跟 项目/库/代码 或前面有 用/写/开发/语言）
                    if (looksLikeLangQuery(q, l)) {
                        intent.filterField = "language";
                        intent.filterValue = l;
                        break;
                    }
                }
            }
        }

        // --- 过滤意图（协议） ---
        if (intent.filterField == null) {
            String[] licenses = {"MIT", "Apache", "GPL", "BSD", "LGPL", "AGPL", "MPL", "EPL", "Unlicense"};
            for (String lic : licenses) {
                if (q.toUpperCase().contains(lic) && (q.contains("协议") || q.contains("许可") || q.contains("开源"))) {
                    intent.filterField = "license";
                    intent.filterValue = lic;
                    break;
                }
            }
        }

        return intent.isStructured() ? intent : null;
    }

    /** 判断 query 中的 lang 是否真的在表达"找某个语言的项目" */
    private boolean looksLikeLangQuery(String q, String lang) {
        // 后面紧跟：项目/代码/库/框架/语言/开源
        int idx = q.indexOf(lang);
        String after = q.substring(idx + lang.length());
        if (after.matches("^\\s*(项目|代码|库|框架|语言|开源|工程|程序|实现|版本).*")) return true;
        // 前面有：找/推荐/要/写/开发/学/做 等
        String before = q.substring(0, idx);
        if (before.matches(".*(找|推荐|要|写|开发|学|做|使用|什么|哪些|给我)\\s*$")) return true;
        // 包含 "语言"
        if (q.contains("语言")) return true;
        return false;
    }

    private boolean containsAny(String str, String... keywords) {
        String lower = str.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    /** 用户意图的解析结果 */
    @lombok.Data
    public static class QueryIntent {
        String filterField;  // "language" / "license" / null
        String filterValue;  // "Java" / "MIT" / null
        String sortField;    // "stars_count" / "created_at" / "pushed_at" / null
        boolean sortDesc;

        boolean isStructured() {
            return sortField != null || filterField != null;
        }
    }

    // ============== DTO ==============

    @lombok.Data
    public static class RagAnswer {
        private String answer;
        private List<RagRef> refs;
        /** 是否使用了知识库数据 (有检索命中) */
        private Boolean usedKnowledge;
        /** 知识来源: "rag" = 强相关项目, "hybrid" = 弱相关项目, "llm" = 大模型自身知识 (降级模式) */
        private String knowledgeSource;
        /** 向量检索命中项目的最高相似度 (0~1)，用于前端排障 */
        private Double highestScore;
        private String sessionId;
        private Long durationMs;
        private boolean success = true;
        private String errorMsg;

        static RagAnswer fail(String msg) {
            RagAnswer r = new RagAnswer();
            r.success = false;
            r.errorMsg = msg;
            return r;
        }
    }

    @lombok.Data
    public static class RagRef {
        private String docId;
        private String title;          // 对应项目 full_name (owner/repo)
        private String author;         // 从 full_name 中抽取的 owner
        private String url;            // https://github.com/ + full_name
        private String category;       // 对应项目 language
        private List<String> topics;
        private String license;
        private Double score;
        private String description;
        private Integer starsCount;
    }
}
