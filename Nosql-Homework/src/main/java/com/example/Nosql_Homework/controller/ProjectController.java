package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.crawler.CrawlerService;
import com.example.Nosql_Homework.entity.*;
import com.example.Nosql_Homework.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final CommitService commitService;
    private final ContributorService contributorService;
    private final CrawlerService crawlerService;

    @GetMapping
    public R<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minStars,
            @RequestParam(defaultValue = "updatedAt") String sortBy) {

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(sortBy).descending());
        PageResult<Project> result = projectService.listProjects(language, category, minStars, pageable);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Project> getById(@PathVariable String id) {
        return projectService.getById(id)
                .map(R::ok)
                .orElse(R.fail(404, "项目不存在"));
    }

    @GetMapping("/{id}/commits")
    public R<PageResult<Commit>> commits(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page - 1, size);
        return R.ok(commitService.listByProject(id, pageable));
    }

    @GetMapping("/{id}/contributors")
    public R<List<Contributor>> contributors(@PathVariable String id) {
        return R.ok(contributorService.listByProject(id));
    }

    /**
     * 按需拉取 commit 历史 (点击"查看 Commit"按钮时调用)
     * POST /api/projects/{id}/fetch-commits
     */
    @PostMapping("/{id}/fetch-commits")
    public R<Map<String, Object>> fetchCommits(@PathVariable String id) {
        int count = crawlerService.fetchCommitsOnDemand(id);
        return R.ok(Map.of("projectId", id, "fetched", count));
    }

    /**
     * 按需拉取 contributor 列表 (进入项目详情时自动调用)
     * POST /api/projects/{id}/fetch-contributors
     */
    @PostMapping("/{id}/fetch-contributors")
    public R<Map<String, Object>> fetchContributors(@PathVariable String id) {
        int count = crawlerService.fetchContributorsOnDemand(id);
        return R.ok(Map.of("projectId", id, "fetched", count));
    }

    /** 语言分布统计 */
    @GetMapping("/language-stats")
    public R<List<Map<String, Object>>> languageStats() {
        return R.ok(projectService.getLanguageStats());
    }

    @PostMapping
    public R<Project> create(@RequestBody Project project) {
        return R.ok(projectService.save(project));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        projectService.deleteById(id);
        return R.ok();
    }

    // ============ Tiger-RAG: 向量补全 ============

    /**
     * 查看项目集合的 embedding 统计（有多少项目已经向量化）
     */
    @GetMapping("/embedding-stats")
    public R<Map<String, Object>> embeddingStats() {
        return R.ok(projectService.embedStats());
    }

    /**
     * 为单个项目生成 / 重新生成向量
     * POST /api/projects/{id}/embed?force=false
     */
    @PostMapping("/{id}/embed")
    public R<Map<String, Object>> embedOne(@PathVariable String id,
                                           @RequestParam(defaultValue = "false") boolean force) {
        boolean ok = projectService.embedProject(id, force);
        return ok ? R.ok(Map.of("id", id, "force", force, "message", "已为该项目生成向量"))
                  : R.fail(500, "生成向量失败，请检查日志");
    }

    /**
     * 为所有尚未生成向量的项目批量生成 embedding（后台异步执行）
     * POST /api/projects/batch-embed?force=false&batchSize=20
     */
    @PostMapping("/batch-embed")
    public R<Map<String, Object>> batchEmbed(@RequestParam(defaultValue = "20") int batchSize,
                                             @RequestParam(defaultValue = "false") boolean force) {
        return R.ok(projectService.startBatchEmbed(batchSize, force));
    }
}
