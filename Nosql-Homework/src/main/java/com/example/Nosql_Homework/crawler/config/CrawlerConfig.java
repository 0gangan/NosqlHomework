package com.example.Nosql_Homework.crawler.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 爬虫线程池 & 限速配置
 */
@Slf4j
@Configuration
public class CrawlerConfig {

    /** 限速间隔 (毫秒) — GitHub API 调用最小间隔 */
    public static final long RATE_LIMIT_MS = 2500;

    /** 线程安全限速锁 */
    private final Object rateLimitLock = new Object();

    /** GitHub API 上次调用时间戳 */
    private long lastCallTime = 0;

    /** 线程计数器 (命名用) */
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    /** 持有 bean 引用, 供 @PreDestroy 关闭 */
    private ExecutorService commitExecutor;

    /**
     * Commits / Contributors 异步拉取线程池
     * 核心 2 线程, 最大 4 线程, 队列 200, 拒绝策略 CallerRuns
     */
    @Bean
    public ExecutorService commitExecutor() {
        commitExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "commit-fetcher-" + threadCounter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("commit 拉取线程池已创建: core=2, max=4, queue=200");
        return commitExecutor;
    }

    /**
     * 简单限速：每次调用间隔至少 RATE_LIMIT_MS (线程安全)
     */
    public void rateLimit() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastCallTime;
            long wait = RATE_LIMIT_MS - elapsed;
            if (wait > 0) {
                log.debug("  限速等待 {}ms (距上次调用 {}ms)", wait, elapsed);
                try {
                    TimeUnit.MILLISECONDS.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("  限速等待被中断", e);
                }
            }
            lastCallTime = System.currentTimeMillis();
        }
    }

    /**
     * 应用关闭时优雅关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        if (commitExecutor == null) return;
        log.info("正在关闭 commit 拉取线程池...");
        commitExecutor.shutdown();
        try {
            if (!commitExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("线程池未在 30s 内完成, 强制关闭");
                commitExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            commitExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("commit 拉取线程池已关闭");
    }
}
