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
}
