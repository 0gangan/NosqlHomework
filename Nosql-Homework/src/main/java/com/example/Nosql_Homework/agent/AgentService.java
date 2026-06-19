package com.example.Nosql_Homework.agent;

import com.example.Nosql_Homework.agent.embedding.EmbeddingService;
import com.example.Nosql_Homework.dto.AgentRequest;
import com.example.Nosql_Homework.dto.AgentResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Agent 智能问答编排服务
 * <p>
 * 链路: 用户提问 → LLM 意图解析 → 检索策略分流 → 向量/精确检索 → Prompt 组装 → LLM 生成回答
 * <p>
 * 核心原则:
 * - LLM 只做两件事: ① 解析意图 ② 生成回答
 * - 中间检索全部由向量相似度 + metadata 精确匹配完成，零 LLM 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final OpenAiChatModel chatModel;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingService embeddingService;

    /**
     * 智能问答入口
     */
    public AgentResponse ask(AgentRequest request) {
        String question = request.getQuestion();
        int topK = request.getTopK();

        log.info("========== Agent 问答开始 ==========");
        log.info("[Agent] 用户提问: \"{}\", topK={}", question, topK);

        long startTime = System.currentTimeMillis();

        // Step 1: LLM 解析问题意图
        QuestionIntent intent = parseQuestionIntent(question);

        log.info("[Agent] 意图解析: time={}, type={}, isTimePrecise={}, isTypePrecise={}",
                intent.time, intent.type, intent.isTimePrecise(), intent.isTypePrecise());

        // Step 2: 检索策略分流
        List<KnowledgeDocument> docs;
        String strategy;

        if (intent.isTimePrecise() || intent.isTypePrecise()) {
            // 精确查询: 用 type + period 过滤
            docs = knowledgeBaseService.searchByMetadata(intent.time, intent.type);
            strategy = "precise";

            // 如果精确命中太多，在结果中用向量再排序 TopK
            if (docs.size() > topK) {
                float[] queryVec = embeddingService.embed(question);
                docs = docs.stream()
                        .sorted((a, b) -> Double.compare(
                                embeddingService.cosineSimilarity(queryVec, b.getEmbedding()),
                                embeddingService.cosineSimilarity(queryVec, a.getEmbedding())))
                        .limit(topK)
                        .collect(Collectors.toList());
                strategy = "hybrid";
            }
        } else {
            // 语义检索: embedding 相似度
            docs = knowledgeBaseService.searchByVector(question, topK);
            strategy = "semantic";
        }

        log.info("[Agent] 检索策略={}, 命中 {} 条文档", strategy, docs.size());
        docs.forEach(d -> log.info("[Agent]   来源: {} ({})", d.getTitle(), d.getType()));

        // Step 3: 组装 Prompt
        String prompt = buildPrompt(docs, question);

        // Step 4: LLM 生成回答
        long llmStart = System.currentTimeMillis();
        String answer;
        double confidence;
        try {
            answer = chatModel.generate(prompt);
            long llmCost = System.currentTimeMillis() - llmStart;
            log.info("[Agent] LLM 回答生成 (耗时 {}ms)", llmCost);
            confidence = docs.isEmpty() ? 0.3 : Math.min(0.95, 0.5 + docs.size() * 0.1);
        } catch (Exception e) {
            log.error("[Agent] LLM 调用失败", e);
            answer = "抱歉，LLM 服务当前不可用，请稍后再试。错误信息: " + e.getMessage();
            confidence = 0.0;
        }

        long totalCost = System.currentTimeMillis() - startTime;
        log.info("[Agent] 问答完成 (总耗时 {}ms)", totalCost);
        log.info("========== Agent 问答结束 ==========");

        return AgentResponse.builder()
                .question(question)
                .answer(answer)
                .sources(docs.stream().map(KnowledgeDocument::getTitle).collect(Collectors.toList()))
                .confidence(confidence)
                .strategy(strategy)
                .build();
    }

    // ======================== 意图解析 ========================

    private QuestionIntent parseQuestionIntent(String question) {
        String prompt = """
                你是一个 GitHub 开源数据分析助手。请解析用户问题的意图，提取以下字段:

                1. time: 时间区间
                   - "今年三月" → "2026-03"
                   - "最近" / "最近一周" → null (这些不是精确时间)
                   - "上个月" / "本月" → "2026-05" / "2026-06"
                   - 没有明确时间 → null
                   当前日期: 2026年6月

                2. type: 问题类型
                   - 问"最火的项目""哪些项目增长快" → "monthly_trend"
                   - 问"哪个语言最流行""Python vs Java" → "language_rank"
                   - 问"AI 品类趋势""哪个品类增长快" → "category_trend"
                   - 问特定项目 → "project_analysis"
                   - 无法判断 → null

                只返回 JSON:
                {"time":"...", "type":"..."}

                用户问题: %s
                """.formatted(question);

        try {
            String response = chatModel.generate(prompt);
            log.info("[Agent] LLM 意图解析响应: {}", response);

            String time = extractJsonStringField(response, "time");
            String type = extractJsonStringField(response, "type");
            return new QuestionIntent(time, type);
        } catch (Exception e) {
            log.warn("[Agent] 意图解析失败，使用默认策略", e);
            return new QuestionIntent(null, null);
        }
    }

    // ======================== Prompt 组装 ========================

    private String buildPrompt(List<KnowledgeDocument> docs, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是一个 GitHub 开源项目分析助手。请根据以下知识库内容回答用户问题。

                知识库内容（按相关度排序）:
                """);

        for (int i = 0; i < docs.size(); i++) {
            sb.append("---\n");
            sb.append(docs.get(i).getContent());
            sb.append("\n");
        }

        sb.append("""

                用户问题: %s

                要求:
                - 回答基于知识库内容，不要编造数据
                - 如果有具体数据（星标数、排名等），请引用
                - 如果知识库不足以回答，请明确说明
                - 用中文回答，简洁专业
                - 在回答末尾列出引用的数据来源
                """.formatted(question));

        return sb.toString();
    }

    // ======================== JSON 解析工具 ========================

    private String extractJsonStringField(String json, String field) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            return "null".equals(value) || value.isEmpty() ? null : value;
        }
        pattern = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*null");
        matcher = pattern.matcher(json);
        if (matcher.find()) return null;
        return null;
    }

    // ======================== 内部类 ========================

    /**
     * 解析后的问题意图
     */
    private record QuestionIntent(String time, String type) {
        boolean isTimePrecise() {
            return time != null && !time.isBlank();
        }

        boolean isTypePrecise() {
            return type != null && !type.isBlank();
        }
    }
}
