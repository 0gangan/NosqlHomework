package com.example.Nosql_Homework.service;

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

    @Value("${tiger.rag.system-prompt:你是一个叫 Tiger 的智能助手，负责回答关于 GitHub 开源项目分析平台的问题。请结合平台已有的项目数据来回答用户问题。}")
    private String systemPrompt;

    @Value("${tiger.rag.top-k:5}")
    private int topK;

    @Value("${tiger.rag.min-score:0.5}")
    private double minScore;

    /** 历史对话最多纳入几轮 */
    private static final int HISTORY_ROUNDS = 6;

    /**
     * 发起一次 RAG 问答（sessionId 可为空，为空则不保存历史）
     */
    public RagAnswer chat(String sessionId, String query) {
        long start = System.currentTimeMillis();

        if (query == null || query.isBlank()) {
            return RagAnswer.fail("问题不能为空");
        }

        log.info("[Tiger-RAG] ========== 开始回答 ==========");
        log.info("[Tiger-RAG] session={}, 问题: {}", sessionId, truncate(query, 120));

        // ---------- (1) 向量检索: 在 projects 集合上做 $vectorSearch ----------
        List<VectorSearchService.ScoredProject> refs = vectorSearchService.searchByText(query, topK, minScore);
        log.info("[Tiger-RAG] 向量检索命中 {} 个项目 (阈值 {})", refs.size(), minScore);

        boolean hasKnowledge = !refs.isEmpty();

        // ---------- (2) 组装 Prompt ----------
        String fullPrompt = buildPrompt(sessionId, query, refs);
        log.debug("[Tiger-RAG] Prompt 长度: {} chars", fullPrompt.length());

        // ---------- (3) 调用大模型 ----------
        String answer;
        try {
            long llmStart = System.currentTimeMillis();
            answer = chatModel.generate(fullPrompt);
            log.info("[Tiger-RAG] 大模型返回，耗时 {} ms, 回答长度: {} chars",
                    System.currentTimeMillis() - llmStart, answer == null ? 0 : answer.length());
        } catch (Exception e) {
            log.error("[Tiger-RAG] 大模型调用失败: {}", e.getMessage(), e);
            return RagAnswer.fail("大模型暂时不可用: " + e.getMessage());
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
            rr.score = r.getScore();
            rr.description = r.getDescription();
            rr.starsCount = r.getStarsCount();
            return rr;
        }).toList();
        result.durationMs = totalMs;
        result.usedKnowledge = hasKnowledge;
        return result;
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
     * 组装给大模型的完整消息：
     *   system role 固定指令
     *   检索到的项目片段（按相似度由高到低）
     *   最近几轮历史对话
     *   用户当前问题
     */
    private String buildPrompt(String sessionId, String query, List<VectorSearchService.ScoredProject> refs) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");

        sb.append("回答规则:\n");
        sb.append("1. 优先参考下面「向量数据库检索到的 GitHub 项目」信息；如果没有检索到相关项目，请用你自身的知识回答。\n");
        sb.append("2. 不要在回答中显式提到「向量数据库」、「embedding」等实现细节；用自然口吻表达。\n");
        sb.append("3. 回答简洁、准确；必要时可以用列表、粗体等 Markdown 语法。\n\n");

        if (refs.isEmpty()) {
            sb.append("【参考项目】\n当前向量数据库没有检索到与该问题高度相关的项目，请以你自身的知识回答。\n\n");
        } else {
            sb.append("【参考项目】(按相关度从高到低，相似度 0~1)\n");
            for (int i = 0; i < refs.size(); i++) {
                VectorSearchService.ScoredProject r = refs.get(i);
                sb.append(String.format("--- 项目 %d (相似度 %.2f) ---\n", i + 1, r.getScore() == null ? 0.0 : r.getScore()));
                if (r.getFullName() != null) sb.append("项目: ").append(r.getFullName()).append("\n");
                if (r.getLanguage() != null) sb.append("编程语言: ").append(r.getLanguage()).append("\n");
                if (r.getCategory() != null) sb.append("分类: ").append(r.getCategory()).append("\n");
                if (r.getTopics() != null && !r.getTopics().isEmpty())
                    sb.append("标签: ").append(String.join(", ", r.getTopics())).append("\n");
                if (r.getStarsCount() != null) sb.append("Stars: ").append(r.getStarsCount()).append("\n");
                if (r.getDescription() != null) sb.append("描述:\n").append(r.getDescription()).append("\n");
                sb.append("\n");
            }
        }

        // 历史对话
        if (sessionId != null && !sessionId.isBlank()) {
            List<ChatMessage> history = chatMessageRepo
                    .findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, HISTORY_ROUNDS * 2))
                    .getContent();
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

    // ============== DTO ==============

    @lombok.Data
    public static class RagAnswer {
        private String answer;
        private List<RagRef> refs;
        private Boolean usedKnowledge;
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
        private String title;          // 对应项目 full_name
        private String category;       // 对应项目 language
        private Double score;
        private String description;
        private Integer starsCount;
    }
}
