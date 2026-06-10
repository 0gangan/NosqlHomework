package com.example.Nosql_Homework.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * GitHub 定时采集任务
 * 每天凌晨 2 点自动执行, 每次采集 5 种主流语言的前 5 页
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerJob implements Job {

    private final CrawlerService crawlerService;

    private static final String[] LANGUAGES = {"Java", "Python", "JavaScript", "Go", "TypeScript"};
    private static final int MAX_PAGES = 5;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("========== 定时采集开始 ==========");
        for (String lang : LANGUAGES) {
            try {
                crawlerService.crawlByLanguage(lang, MAX_PAGES);
            } catch (Exception e) {
                log.error("采集语言 {} 异常: {}", lang, e.getMessage());
            }
        }
        log.info("========== 定时采集结束 ==========");
    }
}
