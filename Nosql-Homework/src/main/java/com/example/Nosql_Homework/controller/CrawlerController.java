package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.crawler.CrawlerService;
import com.example.Nosql_Homework.crawler.util.LanguageNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;

    /**
     * 手动触发采集 (调试用)
     * POST /api/crawler/trigger?language=Java&maxPages=3
     */
    @PostMapping("/trigger")
    public R<String> trigger(
            @RequestParam(defaultValue = "Java") String language,
            @RequestParam(defaultValue = "5") int maxPages) {
        String normalized = LanguageNormalizer.normalize(language);
        new Thread(() -> crawlerService.crawlByLanguage(normalized, maxPages)).start();
        return R.ok("采集任务已启动: language=" + language + ", maxPages=" + maxPages);
    }

    /**
     * 增量回填: 补齐所有缺失 commits/contributors 的项目
     * POST /api/crawler/backfill/missing
     */
    @PostMapping("/backfill/missing")
    public R<String> backfillMissing() {
        new Thread(() -> crawlerService.backfillMissing()).start();
        return R.ok("增量回填任务已启动 (后台异步执行, 请查看日志)");
    }

    /**
     * 全量回填: 重新拉取所有项目的 commits/contributors
     * POST /api/crawler/backfill/all?force=true
     * force=false: 只补齐缺失的
     * force=true:  强制覆盖已有数据
     */
    @PostMapping("/backfill/all")
    public R<String> backfillAll(@RequestParam(defaultValue = "false") boolean force) {
        new Thread(() -> crawlerService.backfillAll(force)).start();
        return R.ok("全量回填任务已启动 (force=" + force + ", 后台异步执行, 请查看日志)");
    }

    /**
     * 一键修复历史数据中的语言名称: 将 "js"/"java" 等归一化为 "JavaScript"/"Java"
     * POST /api/crawler/normalize-languages
     */
    @PostMapping("/normalize-languages")
    public R<Map<String, Object>> normalizeLanguages() {
        Map<String, Object> result = crawlerService.normalizeAllProjectLanguages();
        return R.ok(result);
    }

    /**
     * 回填项目分类: 对 category 为 null 的项目用规则引擎打标
     * POST /api/crawler/backfill-categories
     * GET  /api/crawler/backfill-categories?force=true  对已标记为空串的重试
     */
    @PostMapping("/backfill-categories")
    public R<Map<String, Object>> backfillCategories(
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> result = crawlerService.backfillCategories(force);
        return R.ok(result);
    }
}
