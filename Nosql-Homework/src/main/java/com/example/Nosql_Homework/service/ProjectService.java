package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.entity.Project;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProjectService {

    PageResult<Project> listProjects(String language, String category, Integer minStars, Pageable pageable);

    Optional<Project> getById(String id);

    Optional<Project> getByFullName(String fullName);

    Project save(Project project);

    void deleteById(String id);

    /** 按语言统计项目数, 按数量降序 */
    List<Map<String, Object>> getLanguageStats();

    // ============ Tiger-RAG 向量补全 ============

    /** 查看项目集合的 embedding 统计 */
    Map<String, Object> embedStats();

    /** 为单个项目生成/重新生成向量 */
    boolean embedProject(String projectId, boolean force);

    /** 批量启动向量生成任务 */
    Map<String, Object> startBatchEmbed(int batchSize, boolean force);
}
