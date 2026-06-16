package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.crawler.util.LanguageNormalizer;
import com.example.Nosql_Homework.dto.SearchRequest;
import com.example.Nosql_Homework.dto.SearchResponse;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.SemanticSearchService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private final OpenAiChatModel chatModel;
    private final ProjectRepository projectRepository;

    /**
     * 修饰词黑名单：这些词是排序/过滤条件，不是内容搜索词
     */
    private static final Set<String> MODIFIER_WORDS = Set.of(
            // 中文修饰词
            "高星标", "高星", "热门", "火", "最新", "最近", "新", "最多", "最少",
            "好的", "优秀", "最好的", "流行的", "顶级的", "有名的", "著名的",
            "高人气", "高评分", "评分高", "活跃", "活跃的",
            // 英文修饰词
            "high star", "high stars", "popular", "top", "best", "latest",
            "newest", "most starred", "highest rated", "trending",
            "hot", "famous", "well known", "widely used", "leading"
    );

    /** "高星标" 类关键词对应的最小星标数 */
    private static final int HIGH_STARS_THRESHOLD = 1000;
    /** "热门" 类关键词对应的最小星标数 */
    private static final int POPULAR_THRESHOLD = 500;

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

        log.info("[检索] LLM 意图解析完成 (耗时 {}ms) → intent={}, language={}, category={}, "
                + "keywords={}, sortBy={}, minStars={}",
                llmCost, parsed.intent, parsed.language, parsed.category,
                parsed.keywords, parsed.sortBy, parsed.minStars);

        // 2. 后处理：过滤修饰词 + 推断排序 + 归一化语言
        postProcessKeywords(parsed);
        parsed.language = LanguageNormalizer.normalize(parsed.language);

        log.info("[检索] 后处理完成 → keywords={}, sortBy={}, minStars={}, language={}",
                parsed.keywords, parsed.sortBy, parsed.minStars, parsed.language);

        // 3. MongoDB 查询
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

        // 4. 构建响应
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

    // ======================== LLM 意图解析 ========================

    private ParsedIntent parseIntent(String query) {
        String prompt = """
                你是一个 GitHub 项目检索助手。请将用户查询解析为 JSON，提取以下字段：
                - intent: 查询意图 (project_search 或 trend_analysis)
                - language: 编程语言 (Java/Python/JavaScript/Go/TypeScript/Rust/C++/C...，未提及则为null)
                - category: 软件品类 (web/mobile/desktop/tool/ai/ml/framework/library/cli/api/database...，未提及则为null)
                - keywords: 内容搜索关键词
                    * 只包含"这个项目是做什么的"相关的技术/业务名词，用空格分隔
                    * 例如：用户说"高星标 Web 框架" → keywords="framework web"（"高星标"不是内容词！）
                    * 例如：用户说"Python 机器学习" → keywords="machine learning"
                    * 不要包含：高星标/热门/最新/最好/优秀 等排序修饰词
                    * 无内容词时填 null
                - sortBy: 排序依据 (stars / forks / updated)，根据用户意图推断：
                    * "高星标""热门""火" → stars
                    * "最新""最近" → updated
                    * "fork多" → forks
                    * 未提及则为null
                - minStars: 最低星标数 (整数)
                    * 用户说"高星标""高星" → 1000
                    * "热门""火""流行" → 500
                    * "顶级的""最好的" → 5000
                    * 未提及则为null

                用户查询: %s

                只返回 JSON，不要其他内容：
                {"intent":"...", "language":..., "category":..., "keywords":..., "sortBy":..., "minStars":...}
                """.formatted(query);

        log.info("[LLM] 发送 Prompt (长度={} chars)", prompt.length());

        try {
            long startTime = System.currentTimeMillis();
            String response = chatModel.generate(prompt);
            long costMs = System.currentTimeMillis() - startTime;

            log.info("[LLM] 收到响应 (耗时 {}ms, 长度={} chars): {}", costMs, response.length(), response);

            ParsedIntent result = parseJsonResponse(response);
            log.info("[LLM] JSON 解析结果: intent={}, language={}, category={}, "
                    + "keywords={}, sortBy={}, minStars={}",
                    result.intent, result.language, result.category,
                    result.keywords, result.sortBy, result.minStars);
            return result;
        } catch (Exception e) {
            log.error("[LLM] 调用失败 → 使用关键词兜底策略", e);
            ParsedIntent fallback = fallbackParse(query);
            log.info("[LLM] 兜底解析: intent={}, keywords={}", fallback.intent, fallback.keywords);
            return fallback;
        }
    }

    private ParsedIntent parseJsonResponse(String response) {
        ParsedIntent parsed = new ParsedIntent();
        parsed.intent = extractJsonStringField(response, "intent");
        parsed.language = extractJsonStringField(response, "language");
        parsed.category = extractJsonStringField(response, "category");
        parsed.keywords = extractJsonStringField(response, "keywords");
        parsed.sortBy = extractJsonStringField(response, "sortBy");
        parsed.minStars = extractJsonIntField(response, "minStars");
        return parsed;
    }

    private String extractJsonStringField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            return "null".equals(value) || value.isEmpty() ? null : value;
        }
        // 尝试匹配 null 字面量
        pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*null");
        matcher = pattern.matcher(json);
        if (matcher.find()) return null;
        return null;
    }

    private Integer extractJsonIntField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*null");
        matcher = pattern.matcher(json);
        if (matcher.find()) return null;
        return null;
    }

    // ======================== 后处理 ========================

    /**
     * 后处理：过滤修饰词 + 从查询原文推断缺失的排序/星标条件
     */
    private void postProcessKeywords(ParsedIntent parsed) {
        // 1. 从 keywords 中剔除修饰词
        if (parsed.keywords != null) {
            List<String> words = new ArrayList<>(Arrays.asList(parsed.keywords.split("\\s+")));
            words.removeIf(w -> MODIFIER_WORDS.contains(w.toLowerCase()));
            parsed.keywords = words.isEmpty() ? null : String.join(" ", words);
        }

        // 2. 如果 LLM 没给出 minStars，尝试从原始查询推断
        if (parsed.minStars == null && parsed.sortBy == null) {
            // 从 LLM 返回的原始 keywords 检测（处理 LLM 不听话的情况）
            // 这个逻辑已在上面通过 MODIFIER_WORDS 处理了
        }
    }

    private ParsedIntent fallbackParse(String query) {
        ParsedIntent parsed = new ParsedIntent();
        parsed.intent = "project_search";
        // 兜底也做一遍修饰词过滤
        List<String> words = new ArrayList<>(Arrays.asList(query.split("\\s+")));
        words.removeIf(w -> MODIFIER_WORDS.contains(w.toLowerCase()));
        parsed.keywords = words.isEmpty() ? query : String.join(" ", words);
        return parsed;
    }

    // ======================== MongoDB 查询 ========================

    private List<Project> queryProjects(ParsedIntent parsed, int topK) {
        // 构建排序
        Sort sort = buildSort(parsed.sortBy);

        // 策略 1: language + keywords + minStars 齐全
        if (parsed.language != null && parsed.keywords != null) {
            log.info("[查询] 策略: 语言+关键词 → language={}, keywords={}, minStars={}, sort={}",
                    parsed.language, parsed.keywords, parsed.minStars, parsed.sortBy);

            List<Project> results;
            if (parsed.minStars != null) {
                // 有最小星标：先按语言+星标过滤，再在内存中做关键词匹配
                var page = projectRepository.findByLanguageAndStarsCountGreaterThan(
                        parsed.language, parsed.minStars,
                        PageRequest.of(0, topK * 2, sort));
                log.info("[查询] language+minStars 查询命中 {} 条 (总数: {})",
                        page.getContent().size(), page.getTotalElements());
                results = page.getContent().stream()
                        .filter(p -> matchesKeywords(p, parsed.keywords))
                        .limit(topK)
                        .collect(Collectors.toList());
            } else {
                // 无最小星标：关键词模糊匹配 + 语言过滤
                var page = projectRepository
                        .findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(
                                parsed.keywords, parsed.keywords,
                                PageRequest.of(0, topK * 2));
                log.info("[查询] 关键词模糊查询命中 {} 条 (总数: {})",
                        page.getContent().size(), page.getTotalElements());
                results = page.getContent().stream()
                        .filter(p -> parsed.language.equalsIgnoreCase(p.getLanguage()))
                        .sorted(buildComparator(parsed.sortBy))
                        .limit(topK)
                        .collect(Collectors.toList());
            }
            log.info("[查询] 语言+关键词 最终结果 {} 条", results.size());
            return results;
        }

        // 策略 2: language + minStars (无关键词)
        if (parsed.language != null && parsed.minStars != null) {
            log.info("[查询] 策略: 语言+最小星标 → language={}, minStars={}, sort={}",
                    parsed.language, parsed.minStars, parsed.sortBy);
            var page = projectRepository.findByLanguageAndStarsCountGreaterThan(
                    parsed.language, parsed.minStars, PageRequest.of(0, topK, sort));
            log.info("[查询] 命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 策略 3: 仅 language (按 sort 排序)
        if (parsed.language != null) {
            log.info("[查询] 策略: 仅语言 → language={}, sort={}", parsed.language, parsed.sortBy);
            var page = projectRepository.findByLanguage(parsed.language,
                    PageRequest.of(0, topK, sort));
            log.info("[查询] 命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 策略 4: 仅 keywords
        if (parsed.keywords != null) {
            log.info("[查询] 策略: 仅关键词 → keywords={}, sort={}", parsed.keywords, parsed.sortBy);
            var page = projectRepository
                    .findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(
                            parsed.keywords, parsed.keywords,
                            PageRequest.of(0, topK, sort));
            log.info("[查询] 命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 策略 5: 仅 minStars (无语言/关键词)
        if (parsed.minStars != null) {
            log.info("[查询] 策略: 仅最小星标 → minStars={}, sort={}", parsed.minStars, parsed.sortBy);
            var page = projectRepository.findByStarsCountGreaterThan(
                    parsed.minStars, PageRequest.of(0, topK, sort));
            log.info("[查询] 命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
            return page.getContent();
        }

        // 策略 6: 兜底 — 按 sort 排序全表
        log.info("[查询] 策略: 兜底 → sort={}, topK={}", parsed.sortBy, topK);
        var page = projectRepository.findAll(PageRequest.of(0, topK, sort));
        log.info("[查询] 命中 {} 条 (总数: {})", page.getContent().size(), page.getTotalElements());
        return page.getContent();
    }

    /** 根据 sortBy 构建 Sort 对象 */
    private Sort buildSort(String sortBy) {
        if ("updated".equals(sortBy)) {
            return Sort.by("updatedAt").descending();
        } else if ("forks".equals(sortBy)) {
            return Sort.by("forksCount").descending();
        }
        // 默认（含 "stars" 或 null）：按星标降序
        return Sort.by("starsCount").descending();
    }

    /** 内存排序：当数据库查询无法直接排序时使用 */
    private Comparator<Project> buildComparator(String sortBy) {
        if ("updated".equals(sortBy)) {
            return Comparator.comparing(Project::getUpdatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        } else if ("forks".equals(sortBy)) {
            return Comparator.comparing(Project::getForksCount,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(Project::getStarsCount,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /** 检查项目描述或名称是否包含关键词(支持多词 OR 匹配) */
    private boolean matchesKeywords(Project p, String keywords) {
        String text = (p.getName() + " " + p.getDescription()).toLowerCase();
        return Arrays.stream(keywords.toLowerCase().split("\\s+"))
                .anyMatch(text::contains);
    }

    // ======================== 内部类 ========================

    private static class ParsedIntent {
        String intent;
        String language;
        String category;
        String keywords;
        String sortBy;    // stars / updated / forks / null
        Integer minStars; // 最小星标数
    }
}
