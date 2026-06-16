package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.Commit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommitRepository extends MongoRepository<Commit, String> {

    Optional<Commit> findBySha(String sha);

    Page<Commit> findByProjectIdOrderByCommitDateDesc(String projectId, Pageable pageable);

    /** 检查某个项目是否已有提交数据 */
    boolean existsByProjectId(String projectId);

    /** 删除某个项目的所有提交 (回填前清理旧数据) */
    long deleteByProjectId(String projectId);
}
