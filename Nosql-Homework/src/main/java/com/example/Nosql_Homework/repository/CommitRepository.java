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
}
