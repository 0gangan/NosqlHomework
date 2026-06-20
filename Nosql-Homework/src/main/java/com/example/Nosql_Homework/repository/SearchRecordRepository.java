package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.SearchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchRecordRepository extends MongoRepository<SearchRecord, String> {

    Page<SearchRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
