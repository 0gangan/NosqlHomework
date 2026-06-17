package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {

    Optional<Project> findByGithubId(Long githubId);

    Optional<Project> findByFullName(String fullName);

    Page<Project> findByLanguage(String language, Pageable pageable);

    Page<Project> findByCategory(String category, Pageable pageable);

    List<Project> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    Page<Project> findByLanguageAndStarsCountGreaterThan(String language, Integer minStars, Pageable pageable);

    Page<Project> findByStarsCountGreaterThan(Integer minStars, Pageable pageable);

    Page<Project> findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCase(String descKeyword, String nameKeyword, Pageable pageable);

    /** 查找从未分类的项目 (category == null) */
    List<Project> findByCategoryIsNull();

    /** 按品类精确查询 (不分页，用于回填等批量场景) */
    List<Project> findAllByCategory(String category);
}
