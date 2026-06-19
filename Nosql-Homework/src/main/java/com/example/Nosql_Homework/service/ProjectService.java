package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.entity.Project;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

public interface ProjectService {

    PageResult<Project> listProjects(String language, String category, Integer minStars, Pageable pageable);

    Optional<Project> getById(String id);

    Optional<Project> getByFullName(String fullName);

    Project save(Project project);

    void deleteById(String id);

    /**
     * Tiger-RAG: 为项目生成 embedding（幂等）
     * @param force 如果项目已有 embedding 是否强制重新生成
     */
    boolean embedProject(String projectId, boolean force);

    /**
     * Tiger-RAG: 批量为所有尚未生成向量的项目生成 embedding（后台异步运行）
     * @return 任务摘要（总数、将补全数等）
     */
    Map<String, Object> startBatchEmbed(int batchSize, boolean force);

    /** Tiger-RAG: 返回 embedding 统计（有向量/无向量数量） */
    Map<String, Object> embedStats();
}
