package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.Contributor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributorRepository extends MongoRepository<Contributor, String> {

    List<Contributor> findByProjectIdOrderByContributionsDesc(String projectId);
}
