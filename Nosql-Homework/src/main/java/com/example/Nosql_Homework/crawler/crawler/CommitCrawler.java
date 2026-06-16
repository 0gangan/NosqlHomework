package com.example.Nosql_Homework.crawler.crawler;

import com.example.Nosql_Homework.crawler.GitHubApiClient;
import com.example.Nosql_Homework.entity.Commit;
import com.example.Nosql_Homework.repository.CommitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Commit 集合爬虫 — 负责从 GitHub API 拉取 commits 并幂等入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommitCrawler {

    private final GitHubApiClient apiClient;
    private final CommitRepository commitRepository;

    /**
     * 拉取并保存指定仓库的 commits
     * @param owner     GitHub 用户名/组织名
     * @param repo      仓库名
     * @param projectId MongoDB 中 project 的 _id
     * @param maxPages  最多翻页数
     * @return 成功保存条数
     */
    public int fetchAndSaveCommits(String owner, String repo, String projectId, int maxPages) {
        int saved = 0;

        for (int page = 1; page <= maxPages; page++) {
            apiClient.rateLimit();
            List<Map<String, Object>> commits = apiClient.listCommits(owner, repo, page);
            if (commits.isEmpty()) break;

            for (Map<String, Object> item : commits) {
                try {
                    saveCommit(item, projectId);
                    saved++;
                } catch (Exception e) {
                    log.debug("  保存 commit 失败 sha={}: {}", item.get("sha"), e.getMessage());
                }
            }
            log.info("  commits page={} 拉取 {} 条, 累计保存 {}", page, commits.size(), saved);
        }
        log.info("  commits 采集完成: {}/{}, 共保存 {} 条", owner, repo, saved);
        return saved;
    }

    /** 幂等保存单条 commit (按 sha 去重) */
    private void saveCommit(Map<String, Object> item, String projectId) {
        String sha = (String) item.get("sha");
        if (sha == null || commitRepository.findBySha(sha).isPresent()) {
            return;
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

    /** 项目是否有 commits */
    public boolean hasCommits(String projectId) {
        return commitRepository.existsByProjectId(projectId);
    }

    /** 删除项目所有 commits */
    public void deleteByProject(String projectId) {
        commitRepository.deleteByProjectId(projectId);
    }

    /** 获取项目 commits 总数 */
    public long countByProject(String projectId) {
        return commitRepository.findByProjectIdOrderByCommitDateDesc(
                projectId, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
    }
}
