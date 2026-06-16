package com.example.Nosql_Homework.crawler.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GitHub 搜索 API 返回的仓库 DTO
 */
public record GitHubRepo(
        long id, String name, String fullName, String description,
        String language, int stargazersCount, int forksCount,
        int watchersCount, int openIssuesCount, int size,
        String defaultBranch, String createdAt, String updatedAt,
        Map<String, Object> owner, List<String> topics, Map<String, Object> license
) {

    @SuppressWarnings("unchecked")
    public static GitHubRepo fromMap(Map<String, Object> m) {
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
