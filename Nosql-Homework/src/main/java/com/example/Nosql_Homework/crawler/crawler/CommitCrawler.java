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

    /** GitHub API 每页最大条数 */
    private static final int PER_PAGE = 100;
    /** 每个项目最多拉取 commit 条数 */
    private static final int MAX_COMMITS = 300;

    /**
     * 拉取并保存指定仓库的 commits (最多 {@value MAX_COMMITS} 条)
     * @param owner     GitHub 用户名/组织名
     * @param repo      仓库名
     * @param projectId MongoDB 中 project 的 _id
     * @return 成功保存条数
     */
    public int fetchAndSaveCommits(String owner, String repo, String projectId) {
        return fetchAndSaveCommits(owner, repo, projectId, MAX_COMMITS);
    }

    /**
     * 拉取并保存指定仓库的 commits (自定义最大条数)
     */
    public int fetchAndSaveCommits(String owner, String repo, String projectId, int maxRecords) {
        int saved = 0;
        int remaining = Math.min(maxRecords, MAX_COMMITS);

        for (int page = 1; remaining > 0; page++) {
            apiClient.rateLimit();

            int perPage = Math.min(PER_PAGE, remaining);
            List<Map<String, Object>> commits = apiClient.listCommits(owner, repo, page, perPage);
            if (commits.isEmpty()) break;

            for (Map<String, Object> item : commits) {
                if (saved >= maxRecords) break;
                try {
                    saveCommit(item, projectId);
                    saved++;
                } catch (Exception e) {
                    log.debug("  保存 commit 失败 sha={}: {}", item.get("sha"), e.getMessage());
                }
            }
            remaining = maxRecords - saved;
            log.info("  commits page={} 拉取 {} 条, 累计保存 {} (剩余配额 {})",
                    page, commits.size(), saved, remaining);
        }
        log.info("  commits 采集完成: {}/{}, 共保存 {} 条 (上限: {})", owner, repo, saved, maxRecords);
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
