package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.dto.SearchRequest;
import com.example.Nosql_Homework.dto.SearchResponse;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.SemanticSearchService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private final OpenAiChatModel chatModel;
    private final ProjectRepository projectRepository;

    /** 后端分类枚举，LLM 返回的 category 必须在此集合中，否则丢弃 */
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "ai", "web", "mobile", "desktop", "framework", "library",
            "cli", "api", "database", "devops", "security", "game", "tool", "data"
    );

    @Override
    public SearchResponse search(SearchRequest request) {
        String query = request.getQuery();
        int topK = request.getTopK();

        log.info("========== 自然语言检索开始 ==========");
        log.info("[检索] 原始查询: \"{}\", topK={}", query, topK);

        long startTime = System.currentTimeMillis();

        // 1. LLM 解析意图与过滤条件
        long llmStart = System.currentTimeMillis();
        ParsedIntent parsed = parseIntent(query);
        long llmCost = System.currentTimeMillis() - llmStart;

        log.info("[检索] LLM 意图解析完成 (耗时 {}ms) → intent={}, language={}, category={}, keywords={}",
                llmCost, parsed.intent, parsed.language, parsed.category, parsed.keywords);

        // 2. MongoDB 查询
        long dbStart = System.currentTimeMillis();
        List<Project> projects = queryProjects(parsed, topK);
        long dbCost = System.currentTimeMillis() - dbStart;

        log.info("[检索] MongoDB 查询完成 (耗时 {}ms) → 命中 {} 条",
                dbCost, projects.size());
        if (!projects.isEmpty()) {
            log.debug("[检索] 结果预览: {}",
                    projects.stream()
                            .map(p -> p.getFullName() + "(" + p.getLanguage() + " ⭐" + p.getStarsCount() + ")")
                            .toList());
        }

        // 3. 构建响应
        List<SearchResponse.ProjectItem> items = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            double score = 1.0 - (i * 0.05);
            items.add(SearchResponse.ProjectItem.from(projects.get(i), Math.max(score, 0.5)));
        }

        long totalCost = System.currentTimeMillis() - startTime;
        log.info("[检索] 构建响应完成 → 返回 {} 条结果", items.size());
        log.info("========== 自然语言检索结束 (总耗时 {}ms) ==========", totalCost);

        return SearchResponse.builder()
                .query(query)
                .parsedIntent(parsed.intent)
                .language(parsed.language)
                .category(parsed.category)
                .keywords(parsed.keywords)
                .items(items)
                .build();
    }

    /**
     * 使用 LLM 将自然语言解析为结构化查询条件
     */
    private ParsedIntent parseIntent(String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 GitHub 项目检索助手。请将用户的自然语言查询解析为 JSON 格式，提取以下字段：\n");
        sb.append("- intent: 查询意图 (code_search / trend_analysis / similar_project / project_search)\n");
        sb.append("- language: 编程语言 (如 Java, Python, JavaScript, Go 等，未提及则为 null)\n");
        sb.append("- category: 软件品类，必须从以下列表中选一个（未提及则为 null）:\n");
        sb.append("    ai=AI/机器学习, web=Web开发, mobile=移动开发, desktop=桌面应用,\n");
        sb.append("    framework=框架, library=库/SDK, cli=命令行CLI, api=API服务,\n");
        sb.append("    database=数据库, devops=DevOps, security=安全, game=游戏,\n");
        sb.append("    tool=开发工具, data=数据/分析\n");
        sb.append("    注意：category字段只返回小写英文值，如\"ai\"、\"web\"，不要返回中文\n");
        sb.append("- keywords: 搜索关键词 (提取核心技术名词，必须翻译为英文，用空格分隔，不要返回中文)\n");
        sb.append("\n");
        sb.append("用户查询: ").append(query).append("\n");
        sb.append("\n");
        sb.append("只返回 JSON，不要有其他内容:\n");
        sb.append("{\"intent\":\"...\", \"language\":\"...\", \"category\":\"...\", \"keywords\":\"...\"}\n");
        String prompt = sb.toString();

        log.info("[LLM] 发送 Prompt (长度={} chars): {}", prompt.length(), prompt);

        long startTime = System.currentTimeMillis();
        try {
            String response = chatModel.generate(prompt);
            long costMs = System.currentTimeMillis() - startTime;

            log.info("[LLM] 收到响应 (耗时 {}ms, 长度={} chars): {}", costMs, response.length(), response);

            ParsedIntent result = parseJsonResponse(response);

            log.info("[LLM] JSON 解析结果: intent={}, language={}, category={}, keywords={}",
                    result.intent, result.language, result.category, result.keywords);

            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("[LLM] 意图解析失败: 异常类型={}, 消息={}, rootCause={}, 消息={}, Prompt长度={}, 耗时={}ms → 使用关键词兜底策略",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage(),
                    prompt.length(), cost, e);
            ParsedIntent fallback = fallbackParse(query);
            log.info("[LLM] 兜底解析: intent={}, keywords={}", fallback.intent, fallback.keywords);
            return fallback;
        }
    }

    private ParsedIntent parseJsonResponse(String response) {
        ParsedIntent parsed = new ParsedIntent();
        parsed.intent = extractJsonField(response, "intent");
        parsed.language = extractJsonField(response, "language");
        parsed.category = validateCategory(extractJsonField(response, "category"));
        parsed.keywords = extractJsonField(response, "keywords");
        return parsed;
    }

    /** 校验 LLM 返回的分类值是否在后端分类枚举中，不在则丢弃 */
    private String validateCategory(String category) {
        if (category == null) return null;
        String lower = category.toLowerCase().trim();
        if (VALID_CATEGORIES.contains(lower)) {
            return lower;
        }
        log.warn("[LLM] 分类值 \"{}\" 不在有效分类列表中，已丢弃", category);
        return null;
    }

    private String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            return "null".equals(value) || value.isEmpty() ? null : value;
        }
        return null;
    }

    private ParsedIntent fallbackParse(String query) {
        ParsedIntent parsed = new ParsedIntent();
        parsed.intent = "project_search";
        parsed.keywords = query;
        return parsed;
    }

    /**
     * 根据解析条件查询 MongoDB
     */
    private List<Project> queryProjects(ParsedIntent parsed, int topK) {
        PageRequest pageable = PageRequest.of(0, topK);

        // 优先按分类查询（后端预定义分类，精准匹配）
        if (parsed.category != null) {
            log.info("[查询] 策略: 分类 → category={}", parsed.category);
            var page = projectRepository.findByCategory(parsed.category, pageable);
            log.info("[查询] 分类查询命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 按语言 + 关键词查询
        if (parsed.language != null && parsed.keywords != null) {
            log.info("[查询] 策略: 语言 + 关键词 → language={}, keywords={}", parsed.language, parsed.keywords);
            var page = projectRepository
                    .findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(
                            parsed.keywords, parsed.keywords, pageable);
            log.info("[查询] 关键词查询命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());

            List<Project> filtered = page.getContent().stream()
                    .filter(p -> parsed.language.equalsIgnoreCase(p.getLanguage()))
                    .toList();
            log.info("[查询] 语言过滤后剩余 {} 条", filtered.size());
            return filtered;
        }

        // 只按关键词
        if (parsed.keywords != null) {
            log.info("[查询] 策略: 仅关键词 → keywords={}", parsed.keywords);
            var page = projectRepository
                    .findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(
                            parsed.keywords, parsed.keywords, pageable);
            log.info("[查询] 关键词查询命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 只按语言
        if (parsed.language != null) {
            log.info("[查询] 策略: 仅语言 → language={}", parsed.language);
            var page = projectRepository.findByLanguage(parsed.language, pageable);
            log.info("[查询] 语言查询命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 兜底：按星标排序
        log.info("[查询] 策略: 兜底 → 按星标降序排序, topK={}", topK);
        var page = projectRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, topK,
                        org.springframework.data.domain.Sort.by("starsCount").descending())
        );
        log.info("[查询] 兜底查询命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
        return page.getContent();
    }

    private static class ParsedIntent {
        String intent;
        String language;
        String category;
        String keywords;
    }
}
