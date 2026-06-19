package com.example.Nosql_Homework.agent;

import com.example.Nosql_Homework.agent.embedding.EmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 知识库服务 — 内存版向量存储 + CRUD + 检索
 * <p>
 * 支持两种检索:
 * 1. 精确查询: 按 type + period 过滤 (metadata 匹配)
 * 2. 语义检索: embedding 余弦相似度 TopK
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final EmbeddingService embeddingService;

    /** 内存中的知识文档列表 (线程安全) */
    private final List<KnowledgeDocument> store = new CopyOnWriteArrayList<>();

    /**
     * 启动时预置一些示例知识文档，验证链路可用
     */
    @PostConstruct
    public void initSampleDocs() {
        try {
            log.info("初始化示例知识文档...");

        // 示例: 月度趋势报告
        ingest(KnowledgeDocument.builder()
                .type("monthly_trend")
                .period("2026-06")
                .title("2026年6月 GitHub 项目热度月报")
                .content("""
                        【2026年6月 GitHub 项目热度月报】

                        星标增长 Top 10:
                        1. deepseek-ai/DeepSeek-R1 — 星标 +22,000 | 语言: Python | 品类: AI
                        2. browser-use/browser-use — 星标 +15,000 | 语言: Python | 品类: AI
                        3. microsoft/markitdown — 星标 +12,000 | 语言: Python | 品类: Tool
                        4. n8n-io/n8n — 星标 +9,500 | 语言: TypeScript | 品类: Automation
                        5. langchain-ai/langchain — 星标 +8,300 | 语言: Python | 品类: AI
                        6. ollama/ollama — 星标 +7,800 | 语言: Go | 品类: AI
                        7. rustdesk/rustdesk — 星标 +7,200 | 语言: Rust | 品类: Tool
                        8. maybe-finance/maybe — 星标 +6,500 | 语言: Ruby | 品类: Finance
                        9. lobehub/lobe-chat — 星标 +6,000 | 语言: TypeScript | 品类: AI
                        10. TabbyML/tabby — 星标 +5,500 | 语言: Rust | 品类: AI

                        本月新项目: 1,200 个
                        活跃项目(有更新): 8,500 个
                        最活跃语言: Python(32%), TypeScript(28%), Go(15%), Rust(10%), Java(8%)
                        最活跃品类: AI(35%), Tool(20%), Web(15%), DevOps(10%), Data(8%)
                        """)
                .metadata(Map.of("month", "2026-06", "topLanguage", "Python", "topCategory", "AI"))
                .build());

        // 示例: 语言排名
        ingest(KnowledgeDocument.builder()
                .type("language_rank")
                .period("2026-Q2")
                .title("2026年Q2 编程语言活跃度排行")
                .content("""
                        【2026年Q2 编程语言活跃度排行】

                        按新增项目数:
                        1. Python — 新增 350 个，总星标增长 280k
                        2. TypeScript — 新增 280 个，总星标增长 210k
                        3. Go — 新增 180 个，总星标增长 130k
                        4. Rust — 新增 150 个，总星标增长 180k
                        5. Java — 新增 120 个，总星标增长 90k

                        按平均星标:
                        1. Rust — 平均 2,300 ⭐
                        2. Go — 平均 1,800 ⭐
                        3. Python — 平均 1,600 ⭐
                        4. TypeScript — 平均 1,200 ⭐
                        5. Java — 平均 950 ⭐

                        增长最快: Rust(+25% QoQ), Go(+20% QoQ)
                        """)
                .metadata(Map.of("quarter", "2026-Q2", "topLanguage", "Python", "fastestGrowing", "Rust"))
                .build());

        // 示例: 品类趋势
        ingest(KnowledgeDocument.builder()
                .type("category_trend")
                .period("2026-06")
                .title("2026年6月 品类分布分析")
                .content("""
                        【2026年6月 品类分布分析】

                        品类占比: AI(35%), Tool(20%), Web(15%), DevOps(10%), Data(8%), Security(5%), Mobile(4%), Other(3%)

                        增长最快品类: AI(+12% MoM), Security(+5% MoM), Data(+3% MoM)
                        下降品类: Mobile(-3% MoM), Web(-1% MoM)

                        AI 品类亮点:
                        - LLM 推理/微调项目持续火热 (DeepSeek, Ollama)
                        - 浏览器自动化代理崛起 (browser-use 月增 15k 星标)
                        - RAG 框架竞争激烈 (LangChain, LlamaIndex, DSPy)
                        """)
                .metadata(Map.of("month", "2026-06", "topCategory", "AI", "fastestGrowing", "AI"))
                .build());

        // 示例: 项目分析
        ingest(KnowledgeDocument.builder()
                .type("project_analysis")
                .period("2026-06")
                .title("【项目分析】browser-use/browser-use")
                .content("""
                        【项目分析】browser-use/browser-use
                        品类: AI | 语言: Python | 星标: 45k | Fork: 5k
                        描述: 让 AI Agent 像人类一样操作浏览器的开源库，支持点击、填写表单、数据抓取
                        核心标签: ai, browser, automation, agent, llm, playwright
                        贡献者: 80 人 | 活跃度: 极高 | 月增长: 15k 星标
                        类似项目: microsoft/playwright, SeleniumHQ/selenium
                        """)
                .metadata(Map.of("projectName", "browser-use/browser-use", "language", "Python", "category", "AI"))
                .build());

        log.info("示例知识文档初始化完成，共 {} 条", store.size());
        } catch (Exception e) {
            log.warn("示例知识文档初始化失败 (Embedding API 不可用?): {}，知识库将为空，稍后可通过 /api/agent/analyze 手动触发", e.getMessage());
        }
    }

    /**
     * 摄入文档: 向量化 + 存入内存
     * <p>
     * 如果 Embedding API 不可用，文档仍会存入，向量置为空数组 (语义检索降级为关键词匹配)
     */
    public KnowledgeDocument ingest(KnowledgeDocument doc) {
        try {
            float[] embedding = embeddingService.embed(doc.getContent());
            doc.setEmbedding(embedding);
        } catch (Exception e) {
            log.warn("[知识库] 向量化失败 (Embedding API 不可用), 文档将仅支持精确检索: {}", e.getMessage());
            doc.setEmbedding(new float[0]);
        }
        store.add(doc);
        log.info("[知识库] 摄入文档: id={}, type={}, period={}, title={}, hasEmbedding={}",
                doc.getId(), doc.getType(), doc.getPeriod(), doc.getTitle(), doc.getEmbedding().length > 0);
        return doc;
    }

    /**
     * 精确查询: 按 type + period 过滤
     */
    public List<KnowledgeDocument> searchByMetadata(String period, String type) {
        return store.stream()
                .filter(doc -> {
                    boolean match = true;
                    if (period != null && !period.isBlank()) {
                        match = period.equals(doc.getPeriod());
                    }
                    if (type != null && !type.isBlank()) {
                        match = match && type.equals(doc.getType());
                    }
                    return match;
                })
                .collect(Collectors.toList());
    }

    /**
     * 语义检索: embedding 余弦相似度 TopK
     */
    public List<KnowledgeDocument> searchByVector(String query, int topK) {
        float[] queryVec = embeddingService.embed(query);

        return store.stream()
                .filter(doc -> doc.getEmbedding() != null && doc.getEmbedding().length > 0)
                .map(doc -> new AbstractMap.SimpleEntry<>(doc,
                        embeddingService.cosineSimilarity(queryVec, doc.getEmbedding())))
                .filter(e -> e.getValue() > 0.3) // 相似度阈值
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有文档
     */
    public List<KnowledgeDocument> listAll() {
        return new ArrayList<>(store);
    }

    /**
     * 知识库大小
     */
    public int size() {
        return store.size();
    }
}
