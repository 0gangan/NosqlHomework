package com.example.Nosql_Homework.crawler;

import com.example.Nosql_Homework.crawler.crawler.CommitCrawler;
import com.example.Nosql_Homework.crawler.crawler.ContributorCrawler;
import com.example.Nosql_Homework.crawler.crawler.OwnerCrawler;
import com.example.Nosql_Homework.crawler.crawler.ProjectCrawler;
import com.example.Nosql_Homework.crawler.dto.GitHubRepo;
import com.example.Nosql_Homework.crawler.util.LanguageNormalizer;
import com.example.Nosql_Homework.entity.Contributor;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

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
    private final ExecutorService commitExecutor;

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
                        log.error("  page={} 入库失败 repo={}(id={}): {}", page,
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
            commitCrawler.fetchAndSaveCommits(owner, repo, project.getId(), 3);
        } catch (Exception e) {
            log.error("  拉取 commits 失败 project={}: {}", fullName, e.getMessage());
        }
        try {
            contributorCrawler.fetchAndSaveContributors(owner, repo, project.getId(), 1);
        } catch (Exception e) {
            log.error("  拉取 contributors 失败 project={}: {}", fullName, e.getMessage());
        }

        try {
            long commitTotal = commitCrawler.countByProject(project.getId());
            List<Contributor> contribs = contributorCrawler.listByProject(project.getId());
            projectCrawler.updateCounts(project, (int) commitTotal, contribs.size());
        } catch (Exception e) {
            log.warn("  更新项目计数失败 project={}: {}", fullName, e.getMessage());
        }
    }

    /**
     * 批量回填: 遍历所有项目, 对缺少 commits/contributors 的项目从 GitHub API 补齐
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

            boolean hasCommits = commitCrawler.hasCommits(project.getId());
            boolean hasContributors = contributorCrawler.hasContributors(project.getId());

            try {
                if (force || !hasCommits) {
                    if (force && hasCommits) commitCrawler.deleteByProject(project.getId());
                    int saved = commitCrawler.fetchAndSaveCommits(owner, repo, project.getId(), 5);
                    if (saved > 0) project.setCommitsCount(saved);
                } else {
                    log.info("  commits 已存在, 跳过");
                }
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
                log.info("  ✅ 完成: {} (commits={}, contributors={})",
                        fullName, project.getCommitsCount(), project.getContributorsCount());
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
     * 增量回填: 只处理缺少 commits/contributors 的项目
     */
    public Map<String, Object> backfillMissing() {
        List<Project> allProjects = projectRepository.findAll();
        List<Project> missing = allProjects.stream()
                .filter(p -> !commitCrawler.hasCommits(p.getId())
                        || !contributorCrawler.hasContributors(p.getId()))
                .toList();

        log.info("========== 增量回填: 共 {} 个项目缺失数据 ==========", missing.size());

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
                if (!commitCrawler.hasCommits(project.getId())) {
                    int saved = commitCrawler.fetchAndSaveCommits(owner, repo, project.getId(), 5);
                    if (saved > 0) project.setCommitsCount(saved);
                }
                if (!contributorCrawler.hasContributors(project.getId())) {
                    contributorCrawler.fetchAndSaveContributors(owner, repo, project.getId(), 2);
                    List<Contributor> contribs = contributorCrawler.listByProject(project.getId());
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

    // ======================== 内部 ========================

    /** 保存 Owner + Project, 新项目异步拉取 commits/contributors */
    private void saveRepo(GitHubRepo repo, String language) {
        // 1. Owner
        String ownerId = ownerCrawler.saveOwner(repo.owner()).getId();

        // 2. Project (幂等 upsert)
        ProjectCrawler.ProjectResult result = projectCrawler.saveOrUpdateProject(repo, ownerId, language);

        // 3. 新项目异步拉取 commits & contributors
        if (result.isNew()) {
            final Project captured = result.project();
            commitExecutor.submit(() -> {
                try {
                    saveRepoCommitsAndContributors(captured);
                } catch (Exception e) {
                    log.warn("  异步拉取 commits/contributors 失败 project={}: {}",
                            captured.getFullName(), e.getMessage());
                }
            });
        }
    }
}
