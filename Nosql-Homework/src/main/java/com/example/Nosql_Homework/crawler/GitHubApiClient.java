package com.example.Nosql_Homework.crawler;

import com.example.Nosql_Homework.crawler.config.CrawlerConfig;
import com.example.Nosql_Homework.crawler.dto.GitHubRepo;
import com.example.Nosql_Homework.crawler.exception.GitHubApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Instant;
import java.util.*;

/**
 * GitHub REST API 共享 HTTP 客户端
 * 负责 Token 认证、限速日志、HTTP 调用与异常转换
 */
@Slf4j
@Component
public class GitHubApiClient {

    private final CrawlerConfig crawlerConfig;
    private final RestTemplate restTemplate;

    @Value("${github.tokens:}")
    private String tokenList;

    public GitHubApiClient(CrawlerConfig crawlerConfig) {
        this.crawlerConfig = crawlerConfig;
        this.restTemplate = createRestTemplate();
    }

    // ======================== 公开 API ========================

    /**
     * 搜索仓库 (GET /search/repositories)
     */
    public List<GitHubRepo> searchRepositories(String url, int page) {
        long startTime = System.currentTimeMillis();
        HttpHeaders headers = buildHeaders();

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            long elapsed = System.currentTimeMillis() - startTime;
            logRateLimitHeaders(resp.getHeaders());

            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("  page={} 非2xx响应: status={}, 耗时={}ms", page, resp.getStatusCode(), elapsed);
                return Collections.emptyList();
            }

            Map<String, Object> body = resp.getBody();
            if (body == null) {
                log.warn("  page={} 响应体为空, 耗时={}ms", page, elapsed);
                return Collections.emptyList();
            }

            if (!body.containsKey("items")) {
                log.warn("  page={} 响应体中无items字段, keys={}, 耗时={}ms", page, body.keySet(), elapsed);
                if (body.containsKey("message")) {
                    log.error("  page={} GitHub API 错误: {}", page, body.get("message"));
                }
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            int totalCount = body.containsKey("total_count")
                    ? ((Number) body.get("total_count")).intValue() : -1;
            log.debug("  page={} 响应成功 | 耗时={}ms | total_count={} | items={}",
                    page, elapsed, totalCount, items.size());

            List<GitHubRepo> repos = new ArrayList<>();
            for (Map<String, Object> item : items) {
                try {
                    repos.add(GitHubRepo.fromMap(item));
                } catch (Exception e) {
                    log.error("  page={} 解析单条repo失败: id={}, error={}",
                            page, item.get("id"), e.getMessage(), e);
                }
            }
            return repos;

        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("  page={} 网络连接失败 (耗时{}ms), URL={}", page, elapsed, url, e);
            throw new GitHubApiException(0, "GitHub API 网络连接失败: " + e.getMessage(), e);
        } catch (HttpStatusCodeException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("  page={} HTTP错误 (耗时{}ms): status={}, body={}",
                    page, elapsed, e.getStatusCode(), e.getResponseBodyAsString());
            logRateLimitHeaders(e.getResponseHeaders());
            throw new GitHubApiException(e.getStatusCode().value(),
                    "GitHub API 响应异常, status=" + e.getStatusCode(), e);
        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("  page={} 请求异常 (耗时{}ms): {}", page, elapsed, e.getMessage(), e);
            throw new GitHubApiException(0, "GitHub API 请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 commits 列表 (GET /repos/{owner}/{repo}/commits)
     */
    public List<Map<String, Object>> listCommits(String owner, String repo, int page, int perPage) {
        return fetchList(
                "https://api.github.com/repos/" + owner + "/" + repo + "/commits?per_page=" + perPage + "&page=" + page,
                page, "commits");
    }

    /**
     * 获取 contributors 列表 (GET /repos/{owner}/{repo}/contributors)
     */
    public List<Map<String, Object>> listContributors(String owner, String repo, int page) {
        return fetchList(
                "https://api.github.com/repos/" + owner + "/" + repo + "/contributors?per_page=20&page=" + page,
                page, "contributors");
    }

    /** 触发限速 */
    public void rateLimit() {
        crawlerConfig.rateLimit();
    }

    // ======================== 内部 ========================

    private List<Map<String, Object>> fetchList(String url, int page, String label) {
        HttpHeaders headers = buildHeaders();

        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (resp.getBody() == null || resp.getBody().isEmpty()) {
                return Collections.emptyList();
            }
            return resp.getBody();

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 409) {
                log.info("  仓库 {} 为空或无 {}, 跳过", label, label);
            } else if (e.getStatusCode().value() == 404) {
                log.warn("  {} API 404 page={}", label, page);
            } else if (e.getStatusCode().value() == 204) {
                log.info("  仓库无 {} 数据", label);
            } else {
                log.warn("  {} HTTP {} page={}: {}", label, e.getStatusCode(), page, e.getMessage());
                throw new GitHubApiException(e.getStatusCode().value(),
                        label + " API 异常, status=" + e.getStatusCode(), e);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("  {} 请求失败 page={}: {}", label, page, e.getMessage());
            return Collections.emptyList();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.set("User-Agent", "Nosql-Homework-Crawler");
        addAuth(headers);
        return headers;
    }

    private void addAuth(HttpHeaders headers) {
        if (tokenList != null && !tokenList.isBlank()) {
            String[] tokens = tokenList.split(",");
            String token = tokens[(int) (System.currentTimeMillis() / 1000 % tokens.length)].trim();
            String masked = token.length() > 8
                    ? token.substring(0, 8) + "..."
                    : token.substring(0, Math.min(4, token.length())) + "...";
            log.debug("  使用Token: {}", masked);
            headers.setBearerAuth(token);
        } else {
            log.debug("  未配置Token, 使用无认证请求 (限速更严格)");
        }
    }

    private void logRateLimitHeaders(HttpHeaders headers) {
        if (headers == null) return;
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        String limit = headers.getFirst("X-RateLimit-Limit");
        String reset = headers.getFirst("X-RateLimit-Reset");
        if (remaining != null || limit != null) {
            long resetSec = reset != null ? Long.parseLong(reset) : 0;
            long waitSec = resetSec - Instant.now().getEpochSecond();
            log.info("  RateLimit: {}/{} 剩余, reset在{}秒后",
                    remaining, limit, Math.max(0, waitSec));
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        String proxyHost = System.getProperty("https.proxyHost");
        String proxyPort = System.getProperty("https.proxyPort");
        if (proxyHost != null && proxyPort != null) {
            log.info("检测到系统代理: {}:{}, RestTemplate 将通过代理访问", proxyHost, proxyPort);
        }
        return new RestTemplate(factory);
    }
}
