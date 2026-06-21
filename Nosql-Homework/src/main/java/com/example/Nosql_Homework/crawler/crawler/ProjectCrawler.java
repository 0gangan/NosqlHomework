package com.example.Nosql_Homework.crawler.crawler;

import com.example.Nosql_Homework.crawler.dto.GitHubRepo;
import com.example.Nosql_Homework.crawler.util.CategoryClassifier;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * Project 集合爬虫 — 负责 Project 的幂等 upsert
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCrawler {

    private final ProjectRepository projectRepository;

    /**
     * 按 githubId 幂等 upsert Project
     * @param repo     GitHub API 返回的仓库数据
     * @param ownerId  已保存的 Owner MongoDB _id
     * @param language 默认语言
     * @return 保存后的 Project 及是否为新建 (isNew)
     */
    public ProjectResult saveOrUpdateProject(GitHubRepo repo, String ownerId, String language) {
        Project existing = projectRepository.findByGithubId(repo.id()).orElse(null);

        Project project;
        boolean isNew = (existing == null);

        if (!isNew) {
            project = existing;
            project.setStarsCount(repo.stargazersCount());
            project.setForksCount(repo.forksCount());
            project.setWatchersCount(repo.watchersCount());
            project.setOpenIssuesCount(repo.openIssuesCount());
            project.setUpdatedAt(Date.from(Instant.parse(repo.updatedAt())));
            if (repo.pushedAt() != null) {
                project.setLastPushAt(Date.from(Instant.parse(repo.pushedAt())));
            }
        } else {
            project = Project.builder()
                    .githubId(repo.id())
                    .name(repo.name())
                    .fullName(repo.fullName())
                    .ownerId(ownerId)
                    .description(repo.description())
                    .language(language)
                    .topics(repo.topics())
                    .license(repo.license() != null ? (String) repo.license().get("spdx_id") : null)
                    .starsCount(repo.stargazersCount())
                    .forksCount(repo.forksCount())
                    .watchersCount(repo.watchersCount())
                    .openIssuesCount(repo.openIssuesCount())
                    .sizeKb(repo.size())
                    .defaultBranch(repo.defaultBranch())
                    .lastPushAt(repo.pushedAt() != null
                            ? Date.from(Instant.parse(repo.pushedAt())) : null)
                    .createdAt(Date.from(Instant.parse(repo.createdAt())))
                    .updatedAt(Date.from(Instant.parse(repo.updatedAt())))
                    .build();
        }

        // 分类: 仅新项目 或 历史项目从未分类/上次未匹配成功时尝试
        if (isNew || project.getCategory() == null
                || CategoryClassifier.UNCLASSIFIED_EMPTY.equals(project.getCategory())) {
            String category = CategoryClassifier.classify(project);
            project.setCategory(category);
        }

        project.setCrawledAt(new Date());
        project = projectRepository.save(project);

        log.debug("  Project 已保存: fullName={}, category={}, isNew={}",
                project.getFullName(), project.getCategory(), isNew);
        return new ProjectResult(project, isNew);
    }

    /** 更新项目的 commits/contributors 计数 */
    public void updateCounts(Project project, int commitsCount, int contributorsCount) {
        project.setCommitsCount(commitsCount);
        project.setContributorsCount(contributorsCount);
        projectRepository.save(project);
    }

    /** Project 保存结果 */
    public record ProjectResult(Project project, boolean isNew) {}
}
