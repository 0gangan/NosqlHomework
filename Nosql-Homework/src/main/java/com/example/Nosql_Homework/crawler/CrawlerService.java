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
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ProjectRepository projectRepository;
    private final OwnerRepository ownerRepository;

    @Value("${github.tokens:}")
    private String tokenList;

    /** 每页拉取条数 (GitHub REST API 最大 100) */
    private static final int PER_PAGE = 30;

    /** GitHub 搜索 API 每 token 每分钟最多 30 次; 这里保守取 25 */
    private long lastCallTime = 0;

    /**
     * 按语言增量采集
     * @param language 编程语言, 如 Java / Python / JavaScript
     * @param maxPages 每个语言最多拉取页数
     */
    public void crawlByLanguage(String language, int maxPages) {
        log.info("开始采集 language={}, maxPages={}", language, maxPages);
        String baseUrl = "https://api.github.com/search/repositories"
                + "?q=language:" + language
                + "&sort=stars&order=desc&per_page=" + PER_PAGE;

        for (int page = 1; page <= maxPages; page++) {
            try {
                rateLimit();
                List<GitHubRepo> repos = fetchPage(baseUrl + "&page=" + page);
                if (repos.isEmpty()) break;

                for (GitHubRepo repo : repos) {
                    saveRepo(repo, language);
                }
                log.info("  page={} 完成, 入库 {} 条", page, repos.size());
            } catch (Exception e) {
                log.error("  page={} 失败: {}", page, e.getMessage());
            }
        }
        log.info("采集 language={} 结束", language);
    }

    // ======================== 内部 ========================

    /** 简单限速：每次调用间隔至少 2.5 秒 */
    private void rateLimit() {
        long now = System.currentTimeMillis();
        long wait = 2500 - (now - lastCallTime);
        if (wait > 0) {
            try { TimeUnit.MILLISECONDS.sleep(wait); } catch (InterruptedException ignored) {}
        }
        lastCallTime = System.currentTimeMillis();
    }

    /** 调用 GitHub Search API */
    private List<GitHubRepo> fetchPage(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        addAuth(headers);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        if (resp.getBody() == null || !resp.getBody().containsKey("items")) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("items");
        List<GitHubRepo> repos = new ArrayList<>();
        for (Map<String, Object> item : items) {
            repos.add(GitHubRepo.fromMap(item));
        }
        return repos;
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
            headers.setBearerAuth(token);
        }
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
