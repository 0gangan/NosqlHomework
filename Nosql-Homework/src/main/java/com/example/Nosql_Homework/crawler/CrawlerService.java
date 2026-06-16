package com.example.Nosql_Homework.crawler;

import com.example.Nosql_Homework.crawler.config.CrawlerConfig;
import com.example.Nosql_Homework.entity.Owner;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.entity.Commit;
import com.example.Nosql_Homework.entity.Contributor;
import com.example.Nosql_Homework.repository.OwnerRepository;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.repository.CommitRepository;
import com.example.Nosql_Homework.repository.ContributorRepository;
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
import java.util.concurrent.ExecutorService;

/**
 * GitHub API 采集服务
 * 参考文档: https://docs.github.com/en/rest
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final CrawlerConfig crawlerConfig;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ProjectRepository projectRepository;
    private final OwnerRepository ownerRepository;
    private final CommitRepository commitRepository;
    private final ContributorRepository contributorRepository;
    private final ExecutorService commitExecutor;

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
                crawlerConfig.rateLimit();
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
        boolean isNew = (existing == null);
        if (!isNew) {
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
        project = projectRepository.save(project);

        // 新项目: 异步拉取 commits 和 contributors (线程池, 不阻塞主流程)
        if (isNew) {
            final Project capturedProject = project;
            final String projectFullName = capturedProject.getFullName();
            commitExecutor.submit(() -> {
                try {
                    saveRepoCommitsAndContributors(capturedProject);
                } catch (Exception e) {
                    log.warn("  异步拉取 commits/contributors 失败 project={}: {}", projectFullName, e.getMessage());
                }
            });
        }
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

    // ======================== Commits & Contributors 采集 ========================

    /**
     * 为新爬取的项目拉取 commits 和 contributors 并入库
     */
    public void saveRepoCommitsAndContributors(Project project) {
        String fullName = project.getFullName();
        if (fullName == null || !fullName.contains("/")) {
            log.warn("  项目 fullName 格式异常, 跳过: {}", fullName);
            return;
        }
        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repo = parts[1];

        try {
            fetchCommits(owner, repo, project.getId(), 3);
        } catch (Exception e) {
            log.error("  拉取 commits 失败 project={}: {}", fullName, e.getMessage());
        }

        try {
            fetchContributors(owner, repo, project.getId(), 1);
        } catch (Exception e) {
            log.error("  拉取 contributors 失败 project={}: {}", fullName, e.getMessage());
        }

        // 更新项目的 commits/contributors 计数
        try {
            long commitTotal = commitRepository.findByProjectIdOrderByCommitDateDesc(
                    project.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
            List<Contributor> contribs = contributorRepository.findByProjectIdOrderByContributionsDesc(project.getId());
            project.setCommitsCount((int) commitTotal);
            project.setContributorsCount(contribs.size());
            projectRepository.save(project);
        } catch (Exception e) {
            log.warn("  更新项目计数失败 project={}: {}", fullName, e.getMessage());
        }
    }

    /**
     * 从 GitHub API 拉取 commits 并存入 MongoDB (幂等: 按 sha 去重)
     */
    public int fetchCommits(String owner, String repo, String projectId, int maxPages) {
        String baseUrl = "https://api.github.com/repos/" + owner + "/" + repo
                + "/commits?per_page=100";
        int saved = 0;

        for (int page = 1; page <= maxPages; page++) {
            String url = baseUrl + "&page=" + page;
            try {
                crawlerConfig.rateLimit();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/vnd.github+json");
                headers.set("X-GitHub-Api-Version", "2022-11-28");
                headers.set("User-Agent", "Nosql-Homework-Crawler");
                addAuth(headers);

                ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (resp.getBody() == null || resp.getBody().isEmpty()) {
                    break;
                }

                for (Map<String, Object> item : resp.getBody()) {
                    try {
                        saveCommit(item, projectId);
                        saved++;
                    } catch (Exception e) {
                        log.debug("  保存 commit 失败 sha={}: {}", item.get("sha"), e.getMessage());
                    }
                }
                log.info("  commits page={} 拉取 {} 条, 累计保存 {}", page, resp.getBody().size(), saved);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 409) {
                    log.info("  仓库 {} 为空或无 commits, 跳过", repo);
                } else if (e.getStatusCode().value() == 404) {
                    log.warn("  仓库 {}/{} 不存在或已删除", owner, repo);
                } else {
                    log.warn("  commits HTTP {} page={}: {}", e.getStatusCode(), page, e.getMessage());
                }
                break;
            } catch (Exception e) {
                log.warn("  commits 请求失败 page={}: {}", page, e.getMessage());
                break;
            }
        }
        log.info("  commits 采集完成: {}/{}, 共保存 {} 条", owner, repo, saved);
        return saved;
    }

    /**
     * 从 GitHub API 拉取 contributors 并存入 MongoDB (幂等: 按 projectId + githubId 去重)
     */
    public int fetchContributors(String owner, String repo, String projectId, int maxPages) {
        String baseUrl = "https://api.github.com/repos/" + owner + "/" + repo
                + "/contributors?per_page=100";
        int saved = 0;

        for (int page = 1; page <= maxPages; page++) {
            String url = baseUrl + "&page=" + page;
            try {
                crawlerConfig.rateLimit();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/vnd.github+json");
                headers.set("X-GitHub-Api-Version", "2022-11-28");
                headers.set("User-Agent", "Nosql-Homework-Crawler");
                addAuth(headers);

                ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (resp.getBody() == null || resp.getBody().isEmpty()) {
                    break;
                }

                for (Map<String, Object> item : resp.getBody()) {
                    try {
                        saveContributor(item, projectId);
                        saved++;
                    } catch (Exception e) {
                        log.debug("  保存 contributor 失败 login={}: {}", item.get("login"), e.getMessage());
                    }
                }
                log.info("  contributors page={} 拉取 {} 条, 累计保存 {}", page, resp.getBody().size(), saved);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 204) {
                    log.info("  仓库 {} 无 contributors 数据", repo);
                } else if (e.getStatusCode().value() == 404) {
                    log.warn("  仓库 {}/{} 不存在或已删除", owner, repo);
                } else {
                    log.warn("  contributors HTTP {} page={}: {}", e.getStatusCode(), page, e.getMessage());
                }
                break;
            } catch (Exception e) {
                log.warn("  contributors 请求失败 page={}: {}", page, e.getMessage());
                break;
            }
        }
        log.info("  contributors 采集完成: {}/{}, 共保存 {} 条", owner, repo, saved);
        return saved;
    }

    private void saveCommit(Map<String, Object> item, String projectId) {
        String sha = (String) item.get("sha");
        if (sha == null || commitRepository.findBySha(sha).isPresent()) {
            return; // 已存在, 跳过
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> commitData = (Map<String, Object>) item.get("commit");
        @SuppressWarnings("unchecked")
        Map<String, Object> authorData = commitData != null
                ? (Map<String, Object>) commitData.get("author") : null;

        String authorLogin = authorData != null ? (String) authorData.get("name") : "unknown";
        String message = commitData != null ? (String) commitData.get("message") : "";
        String dateStr = authorData != null ? (String) authorData.get("date") : null;

        Commit commit = Commit.builder()
                .projectId(projectId)
                .sha(sha)
                .authorLogin(authorLogin)
                .message(message)
                .commitDate(dateStr != null ? Date.from(Instant.parse(dateStr)) : new Date())
                .additions(0)
                .deletions(0)
                .build();
        commitRepository.save(commit);
    }

    private void saveContributor(Map<String, Object> item, String projectId) {
        Number githubIdNum = (Number) item.get("id");
        if (githubIdNum == null) return;
        long githubId = githubIdNum.longValue();

        // 幂等: 已存在则更新 contributions
        Contributor existing = contributorRepository.findByProjectIdAndGithubId(projectId, githubId).orElse(null);
        if (existing != null) {
            existing.setContributions((Integer) item.getOrDefault("contributions", existing.getContributions()));
            contributorRepository.save(existing);
            return;
        }

        Contributor contributor = Contributor.builder()
                .projectId(projectId)
                .githubId(githubId)
                .login((String) item.get("login"))
                .avatarUrl((String) item.get("avatar_url"))
                .contributions((Integer) item.getOrDefault("contributions", 0))
                .build();
        contributorRepository.save(contributor);
    }

    // ======================== 批量回填 ========================

    /**
     * 批量回填: 遍历所有项目, 对缺少 commits/contributors 的项目从 GitHub API 补齐
     * @param force 是否强制覆盖已有数据
     */
    public Map<String, Object> backfillAll(boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Project> allProjects = projectRepository.findAll();
        int total = allProjects.size();
        int success = 0;
        int skipped = 0;
        int failed = 0;

        log.info("========== 批量回填开始 ==========");
        log.info("  项目总数: {}, 强制覆盖: {}", total, force);
        result.put("totalProjects", total);
        result.put("force", force);

        for (int i = 0; i < total; i++) {
            Project project = allProjects.get(i);
            String fullName = project.getFullName();
            log.info("[{}/{}] 处理: {}", i + 1, total, fullName);

            if (fullName == null || !fullName.contains("/")) {
                log.warn("  跳过: fullName 格式异常");
                skipped++;
                continue;
            }

            String[] parts = fullName.split("/");
            String owner = parts[0];
            String repo = parts[1];

            boolean hasCommits = commitRepository.existsByProjectId(project.getId());
            boolean hasContributors = contributorRepository.existsByProjectId(project.getId());

            try {
                if (force || !hasCommits) {
                    if (force && hasCommits) {
                        commitRepository.deleteByProjectId(project.getId());
                    }
                    int saved = fetchCommits(owner, repo, project.getId(), 5);
                    if (saved > 0) {
                        project.setCommitsCount(saved);
                    }
                } else {
                    log.info("  commits 已存在, 跳过");
                }

                if (force || !hasContributors) {
                    if (force && hasContributors) {
                        contributorRepository.deleteByProjectId(project.getId());
                    }
                    fetchContributors(owner, repo, project.getId(), 2);
                    List<Contributor> contribs = contributorRepository.findByProjectIdOrderByContributionsDesc(project.getId());
                    project.setContributorsCount(contribs.size());
                } else {
                    log.info("  contributors 已存在, 跳过");
                }

                projectRepository.save(project);
                success++;
                log.info("  ✅ 完成: {} (commits={}, contributors={})",
                        fullName, project.getCommitsCount(), project.getContributorsCount());
            } catch (Exception e) {
                failed++;
                log.error("  ❌ 失败 {}: {}", fullName, e.getMessage());
            }

            // 每 10 个项目打印进度
            if ((i + 1) % 10 == 0) {
                log.info("========== 回填进度: {}/{} (成功{} 跳过{} 失败{}) ==========",
                        i + 1, total, success, skipped, failed);
            }
        }

        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", failed);

        log.info("========== 批量回填结束 ==========");
        log.info("  成功: {}, 跳过: {}, 失败: {}", success, skipped, failed);
        return result;
    }

    /**
     * 增量回填: 只处理缺少 commits/contributors 的项目
     */
    public Map<String, Object> backfillMissing() {
        List<Project> allProjects = projectRepository.findAll();
        List<Project> missing = allProjects.stream()
                .filter(p -> !commitRepository.existsByProjectId(p.getId())
                        || !contributorRepository.existsByProjectId(p.getId()))
                .toList();

        log.info("========== 增量回填: 共 {} 个项目缺失数据 ==========", missing.size());

        // 仅对缺失项目执行 (复用 backfillAll 逻辑但只传 missing)
        // 简单实现: 设置标记后调用原有逻辑
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProjects", allProjects.size());
        result.put("missingProjects", missing.size());

        int success = 0;
        int failed = 0;

        for (int i = 0; i < missing.size(); i++) {
            Project project = missing.get(i);
            String fullName = project.getFullName();
            log.info("[{}/{}] 处理: {}", i + 1, missing.size(), fullName);

            if (fullName == null || !fullName.contains("/")) {
                log.warn("  跳过: fullName 格式异常");
                continue;
            }

            String[] parts = fullName.split("/");
            String owner = parts[0];
            String repo = parts[1];

            try {
                if (!commitRepository.existsByProjectId(project.getId())) {
                    int saved = fetchCommits(owner, repo, project.getId(), 5);
                    if (saved > 0) project.setCommitsCount(saved);
                }
                if (!contributorRepository.existsByProjectId(project.getId())) {
                    fetchContributors(owner, repo, project.getId(), 2);
                    List<Contributor> contribs = contributorRepository
                            .findByProjectIdOrderByContributionsDesc(project.getId());
                    project.setContributorsCount(contribs.size());
                }
                projectRepository.save(project);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("  ❌ 失败 {}: {}", fullName, e.getMessage());
            }
        }

        result.put("success", success);
        result.put("failed", failed);
        log.info("========== 增量回填结束: 成功{} 失败{} ==========", success, failed);
        return result;
    }

    // ======================== DTO ========================

    @SuppressWarnings("unchecked")
    private record GitHubRepo(
            long id, String name, String fullName, String description,
            String language, int stargazersCount, int forksCount,
            int watchersCount, int openIssuesCount, int size,
            String defaultBranch, String createdAt, String updatedAt,
            Map<String, Object> owner, List<String> topics, Map<String, Object> license
    ) {
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
    }
}
