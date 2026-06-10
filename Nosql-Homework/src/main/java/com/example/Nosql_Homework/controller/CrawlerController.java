package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.crawler.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        new Thread(() -> crawlerService.crawlByLanguage(language, maxPages)).start();
        return R.ok("采集任务已启动: language=" + language + ", maxPages=" + maxPages);
    }
}
