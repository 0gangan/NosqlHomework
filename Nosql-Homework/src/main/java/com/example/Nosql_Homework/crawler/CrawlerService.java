package com.example.Nosql_Homework.crawler;

import com.example.Nosql_Homework.crawler.crawler.CommitCrawler;
import com.example.Nosql_Homework.crawler.crawler.ContributorCrawler;
import com.example.Nosql_Homework.crawler.crawler.OwnerCrawler;
import com.example.Nosql_Homework.crawler.crawler.ProjectCrawler;
import com.example.Nosql_Homework.crawler.dto.GitHubRepo;
import com.example.Nosql_Homework.crawler.util.CategoryClassifier;
import com.example.Nosql_Homework.crawler.util.LanguageNormalizer;
import com.example.Nosql_Homework.entity.Contributor;
import com.example.Nosql_Homework.entity.Owner;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.OwnerRepository;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.impl.EmbeddingService;
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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 爬虫编排服务 — 协调四个 Collection 爬虫完成增量采集与回填
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final GitHubApiClient apiClient;
    private final OwnerCrawler ownerCrawler;
    private final ProjectCrawler projectCrawler;
    private final CommitCrawler commitCrawler;
    private final ContributorCrawler contributorCrawler;
    private final ProjectRepository projectRepository;
    private final OwnerRepository ownerRepository;
    private final EmbeddingService embeddingService;

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

    private static final int PER_PAGE = 30;

    private int totalSaved = 0;
    private int totalFailed = 0;

    /**
     * 按语言增量采集
     */
    public void crawlByLanguage(String rawLanguage, int maxPages) {
        String language = LanguageNormalizer.normalize(rawLanguage);
        log.info("========== 开始采集 ==========");
        log.info("  language    = {} (原始: {})", language, rawLanguage);
        log.info("  maxPages    = {}", maxPages);
        log.info("  perPage     = {}", PER_PAGE);
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
                apiClient.rateLimit();
                List<GitHubRepo> repos = apiClient.searchRepositories(pageUrl, page);
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

    /**
     * 按需拉取项目的 commits 并入库 (用户点击"查看Commit"时调用)
     * @return 拉取的 commit 条数
     */
    public int fetchCommitsOnDemand(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));

        String fullName = project.getFullName();
        if (fullName == null || !fullName.contains("/")) {
            throw new IllegalArgumentException("项目 fullName 格式异常: " + fullName);
        }

        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repo = parts[1];

        log.info("[按需拉取] 开始拉取 commits: {}/{}", owner, repo);
        int saved = commitCrawler.fetchAndSaveCommits(owner, repo, projectId);

        long commitTotal = commitCrawler.countByProject(projectId);
        project.setCommitsCount((int) commitTotal);
        projectRepository.save(project);

        log.info("[按需拉取] 完成: {}/{}, 共拉取 {} 条 commits", owner, repo, saved);
        return saved;
    }

    /**
     * 按需拉取项目的 contributors 并入库 (用户进入项目详情时自动调用)
     * @return 拉取的 contributor 条数
     */
    public int fetchContributorsOnDemand(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));

        String fullName = project.getFullName();
        if (fullName == null || !fullName.contains("/")) {
            throw new IllegalArgumentException("项目 fullName 格式异常: " + fullName);
        }

        // 已有 contributor 数据则跳过
        if (contributorCrawler.hasContributors(projectId)) {
            log.info("[按需拉取] contributors 已存在，跳过: {}", fullName);
            return 0;
        }

        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repo = parts[1];

        log.info("[按需拉取] 开始拉取 contributors: {}/{}", owner, repo);
        int saved = contributorCrawler.fetchAndSaveContributors(owner, repo, projectId, 1);

        List<Contributor> contribs = contributorCrawler.listByProject(projectId);
        project.setContributorsCount(contribs.size());
        projectRepository.save(project);

        log.info("[按需拉取] 完成: {}/{}, 共拉取 {} 条 contributors", owner, repo, saved);
        return saved;
    }

    /**
     * 批量回填: 遍历所有项目, 对缺少 contributors 的项目从 GitHub API 补齐
     * (commits 不再回填, 改为按需拉取)
     */
    public Map<String, Object> backfillAll(boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Project> allProjects = projectRepository.findAll();
        int total = allProjects.size();
        int success = 0;
        int skipped = 0;
        int failed = 0;

        log.info("========== 批量回填 contributors 开始 ==========");
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

            boolean hasContributors = contributorCrawler.hasContributors(project.getId());

            try {
                if (force || !hasContributors) {
                    if (force && hasContributors) contributorCrawler.deleteByProject(project.getId());
                    contributorCrawler.fetchAndSaveContributors(owner, repo, project.getId(), 2);
                    List<Contributor> contribs = contributorCrawler.listByProject(project.getId());
                    project.setContributorsCount(contribs.size());
                } else {
                    log.info("  contributors 已存在, 跳过");
                }
                projectRepository.save(project);
                success++;
                log.info("  ✅ 完成: {} (contributors={})", fullName, project.getContributorsCount());
            } catch (Exception e) {
                failed++;
                log.error("  ❌ 失败 {}: {}", fullName, e.getMessage());
            }

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
     * 增量回填: 只处理缺少 contributors 的项目 (commits 改为按需拉取)
     */
    public Map<String, Object> backfillMissing() {
        List<Project> allProjects = projectRepository.findAll();
        List<Project> missing = allProjects.stream()
                .filter(p -> !contributorCrawler.hasContributors(p.getId()))
                .toList();

        log.info("========== 增量回填 contributors: 共 {} 个项目缺失数据 ==========", missing.size());

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
                contributorCrawler.fetchAndSaveContributors(owner, repo, project.getId(), 2);
                List<Contributor> contribs = contributorCrawler.listByProject(project.getId());
                project.setContributorsCount(contribs.size());
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
        Map<String, Object> ownerMap = repo.owner();
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
        Project existing = projectRepository.findByGithubId(repo.id()).orElse(null);
        Project project;
        if (existing != null) {
            project = existing;
            // 只更新变化字段
            project.setStarsCount(repo.stargazersCount());
            project.setForksCount(repo.forksCount());
            project.setWatchersCount(repo.watchersCount());
            project.setOpenIssuesCount(repo.openIssuesCount());
            project.setUpdatedAt(Date.from(Instant.parse(repo.updatedAt())));
        } else {
            project = Project.builder()
                    .githubId(repo.id())
                    .name(repo.name())
                    .fullName(repo.fullName())
                    .ownerId(owner.getId())
                    .description(repo.description())
                    .language(defaultLanguage)
                    .topics(repo.topics())
                    .license(repo.license() != null ? (String) repo.license().get("spdx_id") : null)
                    .starsCount(repo.stargazersCount())
                    .forksCount(repo.forksCount())
                    .watchersCount(repo.watchersCount())
                    .openIssuesCount(repo.openIssuesCount())
                    .sizeKb(repo.size())
                    .defaultBranch(repo.defaultBranch())
                    .createdAt(Date.from(Instant.parse(repo.createdAt())))
                    .updatedAt(Date.from(Instant.parse(repo.updatedAt())))
                    .hasEmbedding(false)
                    .build();
        }
        project.setCrawledAt(new Date());
        projectRepository.save(project);

        // ===== Tiger-RAG: 自动为项目生成 embedding 向量 =====
        try {
            String embedText = project.toEmbeddingText();
            java.util.List<Float> vec = embeddingService.embed(embedText);
            if (vec != null && !vec.isEmpty()) {
                project.setEmbedding(vec);
                project.setEmbeddingModel(embeddingService.getModelName());
                project.setHasEmbedding(true);
                projectRepository.save(project);
                log.debug("  [Tiger-RAG] 项目 {} 向量生成成功, 维度={}", project.getName(), vec.size());
            } else {
                log.warn("  [Tiger-RAG] 项目 {} 向量生成失败 (返回空)", project.getName());
            }
        } catch (Exception e) {
            log.warn("  [Tiger-RAG] 项目 {} 向量生成异常: {} - {}, 已标记 hasEmbedding=false, 可通过 batch-embed 接口补全",
                    project.getName(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 回填项目分类: 遍历所有 category 为 null 的项目，用 CategoryClassifier 打标
     * <ul>
     *   <li>null → 尝试分类，命中则写分类名，否则写 ""</li>
     *   <li>""  (empty) → 默认不重试，除非 force=true</li>
     * </ul>
     * @param force 是否对已标记为 "" 的项目重新尝试分类
     * @return { total, classified, unclassified, retried }
     */
    public Map<String, Object> backfillCategories(boolean force) {
        List<Project> nullProjects = projectRepository.findByCategoryIsNull();
        int total = nullProjects.size();
        int classified = 0;
        int unclassified = 0;
        int retried = 0;

        log.info("========== 分类回填开始: null类项目={}, force={} ==========", total, force);

        for (int i = 0; i < total; i++) {
            Project p = nullProjects.get(i);
            String category = CategoryClassifier.classify(p);
            p.setCategory(category);
            projectRepository.save(p);

            if (CategoryClassifier.UNCLASSIFIED_EMPTY.equals(category)) {
                unclassified++;
            } else {
                classified++;
            }

            if ((i + 1) % 50 == 0) {
                log.info("  进度: {}/{} (已分类: {}, 未匹配: {})", i + 1, total, classified, unclassified);
            }
        }

        // force 模式: 也对已标记为 "" 的项目重试
        if (force) {
            List<Project> emptyProjects = projectRepository.findAllByCategory(
                    CategoryClassifier.UNCLASSIFIED_EMPTY);
            log.info("  force模式: 重试 {} 个已标记为空的的项目", emptyProjects.size());
            for (Project p : emptyProjects) {
                String category = CategoryClassifier.classify(p);
                if (!CategoryClassifier.UNCLASSIFIED_EMPTY.equals(category)) {
                    p.setCategory(category);
                    projectRepository.save(p);
                    retried++;
                    classified++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("classified", classified);
        result.put("unclassified", unclassified);
        result.put("retried", retried);
        result.put("force", force);
        log.info("========== 分类回填结束: 总数={}, 已分类={}, 未匹配={}, 重试成功={} ==========",
                total, classified, unclassified, retried);
        return result;
    }

    /**
     * 一键修复历史数据：遍历所有项目，将 language 字段归一化为标准名
     * 解决历史数据中 "js"/"JavaScript"、"java"/"Java" 等混合存在的问题
     * @return { total, updated } 总数和实际修改数
     */
    public Map<String, Object> normalizeAllProjectLanguages() {
        List<Project> allProjects = projectRepository.findAll();
        int total = allProjects.size();
        int updated = 0;

        log.info("========== 语言归一化修复开始: 共 {} 个项目 ==========", total);

        for (Project p : allProjects) {
            String oldLang = p.getLanguage();
            if (oldLang == null || oldLang.isBlank()) continue;

            String normalized = LanguageNormalizer.normalize(oldLang);
            if (!normalized.equals(oldLang)) {
                log.info("  修复: {} → {}", oldLang, normalized);
                p.setLanguage(normalized);
                projectRepository.save(p);
                updated++;
            }
        }

        log.info("========== 语言归一化完成: 总数={}, 修复={} ==========", total, updated);
        return Map.of("total", total, "updated", updated);
    }
}
