package com.example.Nosql_Homework.agent;

import com.example.Nosql_Homework.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * 离线分析任务服务
 * <p>
 * 从 MongoDB 聚合数据生成知识文档，零 LLM 调用
 * 纯 MongoDB aggregation + Java 字符串模板拼文本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskService {

    private final MongoTemplate mongoTemplate;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 运行所有分析任务
     */
    public List<KnowledgeDocument> runAllTasks(String period) {
        log.info("========== 开始离线分析: period={} ==========");
        List<KnowledgeDocument> docs = new ArrayList<>();

        try {
            docs.add(generateMonthlyTrend(period));
        } catch (Exception e) {
            log.error("月度趋势分析失败", e);
        }
        try {
            docs.add(generateLanguageRank(period));
        } catch (Exception e) {
            log.error("语言排名分析失败", e);
        }
        try {
            docs.add(generateCategoryTrend(period));
        } catch (Exception e) {
            log.error("品类趋势分析失败", e);
        }

        log.info("========== 离线分析完成: 生成 {} 条知识文档 ==========", docs.size());
        return docs;
    }

    // ======================== 月度趋势报告 ========================

    private KnowledgeDocument generateMonthlyTrend(String period) {
        // 1. 星标 Top 10 项目 (MongoDB 聚合)
        List<Project> topProjects = mongoTemplate.find(
                new org.springframework.data.mongodb.core.query.Query()
                        .with(Sort.by(Sort.Direction.DESC, "starsCount"))
                        .limit(10),
                Project.class);

        // 2. 项目总数
        long totalProjects = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), Project.class);

        // 3. 语言分布 (聚合: group by language)
        Aggregation langAgg = newAggregation(
                group("language").count().as("cnt"),
                sort(Sort.Direction.DESC, "cnt"),
                limit(10));
        List<LangStat> langStats = mongoTemplate.aggregate(langAgg, "projects", LangStat.class)
                .getMappedResults();

        // 4. 品类分布 (聚合: group by category)
        Aggregation catAgg = newAggregation(
                group("category").count().as("cnt"),
                sort(Sort.Direction.DESC, "cnt"),
                limit(10));
        List<CatStat> catStats = mongoTemplate.aggregate(catAgg, "projects", CatStat.class)
                .getMappedResults();

        // 5. 拼文本
        String content = buildMonthlyReport(period, topProjects, totalProjects, langStats, catStats);

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .type("monthly_trend")
                .period(period)
                .title(period + " GitHub 项目热度月报")
                .content(content)
                .metadata(Map.of("month", period,
                        "totalProjects", totalProjects,
                        "topLanguage", langStats.isEmpty() ? "N/A" : langStats.get(0).getId()))
                .build();

        return knowledgeBaseService.ingest(doc);
    }

    private String buildMonthlyReport(String period, List<Project> top, long total,
                                       List<LangStat> langStats, List<CatStat> catStats) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period).append(" GitHub 项目热度月报】\n\n");

        sb.append("星标增长 Top 10:\n");
        for (int i = 0; i < top.size(); i++) {
            Project p = top.get(i);
            sb.append(String.format("%d. %s — ⭐ %d | 语言: %s | 品类: %s\n",
                    i + 1, p.getFullName(), p.getStarsCount(),
                    p.getLanguage() != null ? p.getLanguage() : "未知",
                    p.getCategory() != null ? p.getCategory() : "未分类"));
        }

        sb.append("\n项目总数: ").append(total).append(" 个\n");

        sb.append("最活跃语言: ");
        if (!langStats.isEmpty()) {
            sb.append(langStats.stream()
                    .map(l -> String.format("%s(%d)", l.getId(), l.getCnt()))
                    .collect(Collectors.joining(", ")));
        }
        sb.append("\n");

        sb.append("最活跃品类: ");
        if (!catStats.isEmpty()) {
            sb.append(catStats.stream()
                    .map(c -> String.format("%s(%d)", c.getId(), c.getCnt()))
                    .collect(Collectors.joining(", ")));
        }
        sb.append("\n");

        return sb.toString();
    }

    // ======================== 语言排名报告 ========================

    private KnowledgeDocument generateLanguageRank(String period) {
        // 按语言聚合: 项目数量 + 总星标
        Aggregation agg = newAggregation(
                group("language")
                        .count().as("projectCount")
                        .sum("starsCount").as("totalStars")
                        .avg("starsCount").as("avgStars"),
                sort(Sort.Direction.DESC, "projectCount"),
                limit(10));
        List<LangRankStat> stats = mongoTemplate.aggregate(agg, "projects", LangRankStat.class)
                .getMappedResults();

        String content = buildLanguageRankReport(period, stats);

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .type("language_rank")
                .period(period)
                .title(period + " 编程语言活跃度排行")
                .content(content)
                .metadata(Map.of("period", period,
                        "topLanguage", stats.isEmpty() ? "N/A" : stats.get(0).getId()))
                .build();

        return knowledgeBaseService.ingest(doc);
    }

    private String buildLanguageRankReport(String period, List<LangRankStat> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period).append(" 编程语言活跃度排行】\n\n");

        sb.append("按新增项目数:\n");
        for (int i = 0; i < stats.size(); i++) {
            LangRankStat s = stats.get(i);
            sb.append(String.format("%d. %s — %d 个项目，总星标 %d\n",
                    i + 1, s.getId(), s.getProjectCount(), s.getTotalStars()));
        }

        // 按平均星标重新排序
        sb.append("\n按平均星标:\n");
        stats.stream()
                .sorted((a, b) -> Integer.compare(b.getAvgStars(), a.getAvgStars()))
                .limit(10)
                .forEach(s -> sb.append(String.format("- %s — 平均 %d ⭐\n", s.getId(), s.getAvgStars())));

        return sb.toString();
    }

    // ======================== 品类趋势报告 ========================

    private KnowledgeDocument generateCategoryTrend(String period) {
        Aggregation agg = newAggregation(
                group("category").count().as("cnt"),
                sort(Sort.Direction.DESC, "cnt"),
                limit(15));
        List<CatStat> stats = mongoTemplate.aggregate(agg, "projects", CatStat.class)
                .getMappedResults();

        long total = stats.stream().mapToLong(CatStat::getCnt).sum();

        String content = buildCategoryTrendReport(period, stats, total);

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .type("category_trend")
                .period(period)
                .title(period + " 品类分布分析")
                .content(content)
                .metadata(Map.of("period", period,
                        "topCategory", stats.isEmpty() ? "N/A" : stats.get(0).getId()))
                .build();

        return knowledgeBaseService.ingest(doc);
    }

    private String buildCategoryTrendReport(String period, List<CatStat> stats, long total) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period).append(" 品类分布分析】\n\n");

        sb.append("品类占比: ");
        sb.append(stats.stream()
                .map(s -> String.format("%s(%.0f%%)", s.getId(), 100.0 * s.getCnt() / total))
                .collect(Collectors.joining(", ")));
        sb.append("\n");

        return sb.toString();
    }

    // ======================== 聚合结果 POJO ========================

    @lombok.Data
    public static class LangStat {
        @org.springframework.data.annotation.Id
        private String id;  // language name
        private long cnt;
    }

    @lombok.Data
    public static class CatStat {
        @org.springframework.data.annotation.Id
        private String id;  // category name
        private long cnt;
    }

    @lombok.Data
    public static class LangRankStat {
        @org.springframework.data.annotation.Id
        private String id;  // language name
        private int projectCount;
        private int totalStars;
        private int avgStars;
    }
}
