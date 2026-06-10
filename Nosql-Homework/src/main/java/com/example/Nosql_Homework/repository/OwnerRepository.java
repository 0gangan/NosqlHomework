package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.Owner;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerRepository extends MongoRepository<Owner, String> {

    Optional<Owner> findByGithubId(Long githubId);

    Optional<Owner> findByLogin(String login);

    List<Owner> findByType(String type);

    List<Owner> findAllByOrderByActivityScoreDesc();
}
