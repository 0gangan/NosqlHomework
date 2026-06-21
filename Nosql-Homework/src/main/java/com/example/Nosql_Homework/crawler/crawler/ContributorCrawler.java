package com.example.Nosql_Homework.crawler.crawler;

import com.example.Nosql_Homework.crawler.GitHubApiClient;
import com.example.Nosql_Homework.entity.Contributor;
import com.example.Nosql_Homework.repository.ContributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Contributor 集合爬虫 — 负责从 GitHub API 拉取 contributors 并幂等入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContributorCrawler {

    private final GitHubApiClient apiClient;
    private final ContributorRepository contributorRepository;

    /**
     * 拉取并保存指定仓库的 contributors
     * @param owner     GitHub 用户名/组织名
     * @param repo      仓库名
     * @param projectId MongoDB 中 project 的 _id
     * @param maxPages  最多翻页数
     * @return 成功保存条数
     */
    public int fetchAndSaveContributors(String owner, String repo, String projectId, int maxPages) {
        int saved = 0;

        for (int page = 1; page <= maxPages; page++) {
            apiClient.rateLimit();
            List<Map<String, Object>> contributors = apiClient.listContributors(owner, repo, page);
            if (contributors.isEmpty()) break;

            for (Map<String, Object> item : contributors) {
                try {
                    saveContributor(item, projectId);
                    saved++;
                } catch (Exception e) {
                    log.debug("  保存 contributor 失败 login={}: {}", item.get("login"), e.getMessage());
                }
            }
            log.info("  contributors page={} 拉取 {} 条, 累计保存 {}", page, contributors.size(), saved);
        }
        log.info("  contributors 采集完成: {}/{}, 共保存 {} 条", owner, repo, saved);
        return saved;
    }

    /** 幂等保存单条 contributor (按 projectId + githubId 去重) */
    private void saveContributor(Map<String, Object> item, String projectId) {
        Number githubIdNum = (Number) item.get("id");
        if (githubIdNum == null) return;
        long githubId = githubIdNum.longValue();

        Contributor existing = contributorRepository
                .findByProjectIdAndGithubId(projectId, githubId).orElse(null);
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

    /** 项目是否有 contributors */
    public boolean hasContributors(String projectId) {
        return contributorRepository.existsByProjectId(projectId);
    }

    /** 删除项目所有 contributors */
    public void deleteByProject(String projectId) {
        contributorRepository.deleteByProjectId(projectId);
    }

    /** 获取项目 contributors 列表 */
    public List<Contributor> listByProject(String projectId) {
        return contributorRepository.findByProjectIdOrderByContributionsDesc(projectId);
    }
}
