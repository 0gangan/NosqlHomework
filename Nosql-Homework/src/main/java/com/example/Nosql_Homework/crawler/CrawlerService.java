package com.example.Nosql_Homework.crawler;

import com.example.Nosql_Homework.entity.Owner;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.OwnerRepository;
import com.example.Nosql_Homework.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * GitHub API 采集服务
 * 参考文档: https://docs.github.com/en/rest
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final RestTemplate restTemplate = createRestTemplate();
    private final ProjectRepository projectRepository;
    private final OwnerRepository ownerRepository;

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);  // 连接超时 10 秒
        factory.setReadTimeout(30_000);     // 读取超时 30 秒
        // 自动使用系统代理 (兼容 Clash/V2Ray 等)
        String proxyHost = System.getProperty("https.proxyHost");
        String proxyPort = System.getProperty("https.proxyPort");
        if (proxyHost != null && proxyPort != null) {
            log.info("检测到系统代理: {}:{}, RestTemplate 将通过代理访问", proxyHost, proxyPort);
        }
        return new RestTemplate(factory);
    }

    @Value("${github.tokens:}")
    private String tokenList;

    /** 每页拉取条数 (GitHub REST API 最大 100) */
    private static final int PER_PAGE = 30;

    /** 限速间隔 (毫秒) */
    private static final long RATE_LIMIT_MS = 2500;

    /** GitHub 搜索 API 每 token 每分钟最多 30 次 */
    private long lastCallTime = 0;

    /** 成功入库计数 (统计用) */
    private int totalSaved = 0;
    /** 失败计数 (统计用) */
    private int totalFailed = 0;

    /**
     * 按语言增量采集
     * @param language 编程语言, 如 Java / Python / JavaScript
     * @param maxPages 每个语言最多拉取页数
     */
    public void crawlByLanguage(String language, int maxPages) {
        log.info("========== 开始采集 ==========");
        log.info("  language    = {}", language);
        log.info("  maxPages    = {}", maxPages);
        log.info("  perPage     = {}", PER_PAGE);
        log.info("  tokens配置  = {}", tokenList != null && !tokenList.isBlank() ? "已配置 (" + tokenList.split(",").length + "个)" : "未配置");
        log.info("==============================");

        totalSaved = 0;
        totalFailed = 0;
        String baseUrl = "https://api.github.com/search/repositories"
                + "?q=language:" + language
                + "&sort=stars&order=desc&per_page=" + PER_PAGE;

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = baseUrl + "&page=" + page;
            try {
                log.debug("  请求 page={} URL={}", page, pageUrl);
                rateLimit();
                List<GitHubRepo> repos = fetchPage(pageUrl, page);
                if (repos.isEmpty()) {
                    log.info("  page={} 返回空, 停止翻页", page);
                    break;
                }

                int pageSaved = 0;
                int pageFailed = 0;
                for (GitHubRepo repo : repos) {
                    try {
                        saveRepo(repo, language);
                        pageSaved++;
                    } catch (Exception e) {
                        pageFailed++;
                        log.error("  page={} 入库失败 repo={}(id={}): {} - 堆栈: ", page,
                                repo.fullName(), repo.id(), e.getMessage(), e);
                    }
                }
                totalSaved += pageSaved;
                totalFailed += pageFailed;
                log.info("  page={} 完成 | 拉取{}条 | 成功入库{} | 入库失败{}",
                        page, repos.size(), pageSaved, pageFailed);
            } catch (Exception e) {
                totalFailed++;
                log.error("  page={} 请求/解析失败: {}", page, e.getMessage(), e);
                log.error("  失败 URL: {}", pageUrl);
            }
        }
        log.info("========== 采集结束 ==========");
        log.info("  language = {} | 总计入库 {} | 失败 {}", language, totalSaved, totalFailed);
        log.info("==============================");
    }

    // ======================== 内部 ========================

    /** 简单限速：每次调用间隔至少 RATE_LIMIT_MS */
    private void rateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTime;
        long wait = RATE_LIMIT_MS - elapsed;
        if (wait > 0) {
            log.debug("  限速等待 {}ms (距上次调用 {}ms)", wait, elapsed);
            try { TimeUnit.MILLISECONDS.sleep(wait); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("  限速等待被中断", e);
            }
        }
        lastCallTime = System.currentTimeMillis();
    }

    /** 调用 GitHub Search API */
    private List<GitHubRepo> fetchPage(String url, int page) {
        long startTime = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.set("User-Agent", "Nosql-Homework-Crawler");
        addAuth(headers);

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            long elapsed = System.currentTimeMillis() - startTime;

            // 打印 Rate Limit 信息
            logRateLimitHeaders(resp.getHeaders());

            HttpStatusCode statusCode = resp.getStatusCode();
            if (!statusCode.is2xxSuccessful()) {
                log.warn("  page={} 非2xx响应: status={}, 耗时={}ms", page, statusCode, elapsed);
                return Collections.emptyList();
            }

            if (resp.getBody() == null) {
                log.warn("  page={} 响应体为空, status={}, 耗时={}ms", page, statusCode, elapsed);
                return Collections.emptyList();
            }

            if (!resp.getBody().containsKey("items")) {
                log.warn("  page={} 响应体中无items字段, body keys={}, 耗时={}ms",
                        page, resp.getBody().keySet(), elapsed);
                // 可能是被限流了
                if (resp.getBody().containsKey("message")) {
                    log.error("  page={} GitHub API 错误信息: {}", page, resp.getBody().get("message"));
                }
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("items");
            int totalCount = resp.getBody().containsKey("total_count")
                    ? ((Number) resp.getBody().get("total_count")).intValue() : -1;
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
            log.error("  page={} 网络连接失败 (耗时{}ms)", page, elapsed);
            log.error("  目标 URL: {}", url);
            log.error("  原始异常: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("  根因: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }
            log.error("  提示: 请检查网络连接/代理/VPN 是否正常", e);
            throw e;
        } catch (HttpStatusCodeException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("  page={} HTTP错误 (耗时{}ms): status={}", page, elapsed, e.getStatusCode());
            log.error("  响应体: {}", e.getResponseBodyAsString());
            logRateLimitHeaders(e.getResponseHeaders());
            throw e;
        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("  page={} 请求异常 (耗时{}ms): {} - {}",
                    page, elapsed, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /** 打印 Rate Limit 相关响应头 */
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

    /** 保存项目与 Owner 到 MongoDB */
    private void saveRepo(GitHubRepo repo, String defaultLanguage) {
        // Owner
        Map<String, Object> ownerMap = repo.owner;
        long ownerId = ((Number) ownerMap.get("id")).longValue();
        Owner owner = ownerRepository.findByGithubId(ownerId).orElseGet(() -> Owner.builder()
                .githubId(ownerId)
                .login((String) ownerMap.get("login"))
                .type((String) ownerMap.get("type"))
                .avatarUrl((String) ownerMap.get("avatar_url"))
                .htmlUrl((String) ownerMap.get("html_url"))
                .createdAt(new Date())
                .build());
        owner.setUpdatedAt(new Date());
        owner = ownerRepository.save(owner);

        // Project (幂等: 按 github_id upsert)
        Project existing = projectRepository.findByGithubId(repo.id).orElse(null);
        Project project;
        if (existing != null) {
            project = existing;
            // 只更新变化字段
            project.setStarsCount(repo.stargazersCount);
            project.setForksCount(repo.forksCount);
            project.setWatchersCount(repo.watchersCount);
            project.setOpenIssuesCount(repo.openIssuesCount);
            project.setUpdatedAt(Date.from(Instant.parse(repo.updatedAt)));
        } else {
            project = Project.builder()
                    .githubId(repo.id)
                    .name(repo.name)
                    .fullName(repo.fullName)
                    .ownerId(owner.getId())
                    .description(repo.description)
                    .language(defaultLanguage)
                    .topics(repo.topics)
                    .license(repo.license != null ? (String) repo.license.get("spdx_id") : null)
                    .starsCount(repo.stargazersCount)
                    .forksCount(repo.forksCount)
                    .watchersCount(repo.watchersCount)
                    .openIssuesCount(repo.openIssuesCount)
                    .sizeKb(repo.size)
                    .defaultBranch(repo.defaultBranch)
                    .createdAt(Date.from(Instant.parse(repo.createdAt)))
                    .updatedAt(Date.from(Instant.parse(repo.updatedAt)))
                    .build();
        }
        project.setCrawledAt(new Date());
        projectRepository.save(project);
    }

    /** 设置 Token 认证头 */
    private void addAuth(HttpHeaders headers) {
        if (tokenList != null && !tokenList.isBlank()) {
            String[] tokens = tokenList.split(",");
            String token = tokens[(int) (System.currentTimeMillis() / 1000 % tokens.length)].trim();
            // 只打印 token 前 8 位, 避免泄露
            String masked = token.length() > 8 ? token.substring(0, 8) + "..." : token.substring(0, Math.min(4, token.length())) + "...";
            log.debug("  使用Token: {}", masked);
            headers.setBearerAuth(token);
        } else {
            log.debug("  未配置Token, 使用无认证请求 (限速更严格)");
        }
    }

    // ======================== DTO ========================

    @SuppressWarnings("unchecked")
    private static class GitHubRepo {
        final long id;
        final String name;
        final String fullName;
        final String description;
        final String language;
        final int stargazersCount;
        final int forksCount;
        final int watchersCount;
        final int openIssuesCount;
        final int size;
        final String defaultBranch;
        final String createdAt;
        final String updatedAt;
        final Map<String, Object> owner;
        final List<String> topics;
        final Map<String, Object> license;

        GitHubRepo(long id, String name, String fullName, String description,
                   String language, int stargazersCount, int forksCount,
                   int watchersCount, int openIssuesCount, int size,
                   String defaultBranch, String createdAt, String updatedAt,
                   Map<String, Object> owner, List<String> topics, Map<String, Object> license) {
            this.id = id;
            this.name = name;
            this.fullName = fullName;
            this.description = description;
            this.language = language;
            this.stargazersCount = stargazersCount;
            this.forksCount = forksCount;
            this.watchersCount = watchersCount;
            this.openIssuesCount = openIssuesCount;
            this.size = size;
            this.defaultBranch = defaultBranch;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.owner = owner;
            this.topics = topics;
            this.license = license;
        }

        static GitHubRepo fromMap(Map<String, Object> m) {
            return new GitHubRepo(
                    ((Number) m.get("id")).longValue(),
                    (String) m.get("name"),
                    (String) m.get("full_name"),
                    (String) m.get("description"),
                    (String) m.get("language"),
                    (Integer) m.getOrDefault("stargazers_count", 0),
                    (Integer) m.getOrDefault("forks_count", 0),
                    (Integer) m.getOrDefault("watchers_count", 0),
                    (Integer) m.getOrDefault("open_issues_count", 0),
                    (Integer) m.getOrDefault("size", 0),
                    (String) m.get("default_branch"),
                    (String) m.get("created_at"),
                    (String) m.get("updated_at"),
                    (Map<String, Object>) m.get("owner"),
                    m.containsKey("topics") ? (List<String>) m.get("topics") : Collections.emptyList(),
                    (Map<String, Object>) m.get("license")
            );
        }

        // ===== getter 方法 (保持与 record 一致的调用风格) =====
        public long id() { return id; }
        public String name() { return name; }
        public String fullName() { return fullName; }
        public String description() { return description; }
        public String language() { return language; }
        public int stargazersCount() { return stargazersCount; }
        public int forksCount() { return forksCount; }
        public int watchersCount() { return watchersCount; }
        public int openIssuesCount() { return openIssuesCount; }
        public int size() { return size; }
        public String defaultBranch() { return defaultBranch; }
        public String createdAt() { return createdAt; }
        public String updatedAt() { return updatedAt; }
        public Map<String, Object> owner() { return owner; }
        public List<String> topics() { return topics; }
        public Map<String, Object> license() { return license; }
    }
}
