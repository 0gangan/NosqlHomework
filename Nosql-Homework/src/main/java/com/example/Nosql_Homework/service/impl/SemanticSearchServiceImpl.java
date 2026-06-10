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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private final OpenAiChatModel chatModel;
    private final ProjectRepository projectRepository;

    @Override
    public SearchResponse search(SearchRequest request) {
        String query = request.getQuery();

        // 1. LLM 解析意图与过滤条件
        ParsedIntent parsed = parseIntent(query);

        log.info("查询: {} → 意图: {}, 语言: {}, 品类: {}, 关键词: {}",
                query, parsed.intent, parsed.language, parsed.category, parsed.keywords);

        // 2. MongoDB 查询
        List<Project> projects = queryProjects(parsed, request.getTopK());

        // 3. 构建响应
        List<SearchResponse.ProjectItem> items = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            double score = 1.0 - (i * 0.05); // 简单排名衰减
            items.add(SearchResponse.ProjectItem.from(projects.get(i), Math.max(score, 0.5)));
        }

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
        String prompt = """
                你是一个 GitHub 项目检索助手。请将用户的自然语言查询解析为 JSON 格式，提取以下字段：
                - intent: 查询意图 (code_search / trend_analysis / similar_project / project_search)
                - language: 编程语言 (如 Java, Python, JavaScript, Go 等，未提及则为 null)
                - category: 软件品类 (如 web, mobile, desktop, tool, ai 等，未提及则为 null)
                - keywords: 搜索关键词 (提取核心技术名词，用空格分隔)

                用户查询: %s

                只返回 JSON，不要有其他内容:
                {"intent":"...", "language":"...", "category":"...", "keywords":"..."}
                """.formatted(query);

        try {
            String response = chatModel.generate(prompt);
            log.debug("LLM 响应: {}", response);

            // 简单 JSON 解析（避免引入额外依赖）
            return parseJsonResponse(response);
        } catch (Exception e) {
            log.warn("LLM 解析失败，使用关键词兜底: {}", e.getMessage());
            return fallbackParse(query);
        }
    }

    private ParsedIntent parseJsonResponse(String response) {
        ParsedIntent parsed = new ParsedIntent();
        parsed.intent = extractJsonField(response, "intent");
        parsed.language = extractJsonField(response, "language");
        parsed.category = extractJsonField(response, "category");
        parsed.keywords = extractJsonField(response, "keywords");
        return parsed;
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

        // 优先按语言 + 关键词查询
        if (parsed.language != null && parsed.keywords != null) {
            return projectRepository
                    .findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(
                            parsed.keywords, parsed.keywords, pageable)
                    .stream()
                    .filter(p -> parsed.language.equalsIgnoreCase(p.getLanguage()))
                    .toList();
        }

        // 只按关键词
        if (parsed.keywords != null) {
            return projectRepository
                    .findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(
                            parsed.keywords, parsed.keywords, pageable)
                    .getContent();
        }

        // 只按语言
        if (parsed.language != null) {
            return projectRepository.findByLanguage(parsed.language, pageable).getContent();
        }

        // 兜底：按星标排序
        return projectRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, topK,
                        org.springframework.data.domain.Sort.by("starsCount").descending())
        ).getContent();
    }

    private static class ParsedIntent {
        String intent;
        String language;
        String category;
        String keywords;
    }
}
