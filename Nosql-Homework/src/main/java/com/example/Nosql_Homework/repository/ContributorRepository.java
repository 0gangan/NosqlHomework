package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.Contributor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContributorRepository extends MongoRepository<Contributor, String> {

    List<Contributor> findByProjectIdOrderByContributionsDesc(String projectId);

    /** 按项目ID + GitHub用户ID 精确查找 (用于幂等 upsert) */
    Optional<Contributor> findByProjectIdAndGithubId(String projectId, Long githubId);

    /** 检查某个项目是否已有贡献者数据 */
    boolean existsByProjectId(String projectId);

    /** 删除某个项目的所有贡献者 (回填前清理旧数据) */
    long deleteByProjectId(String projectId);
}
