package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.AnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisResultRepository extends MongoRepository<AnalysisResult, String> {

    List<AnalysisResult> findByAnalysisTypeOrderByCreatedAtDesc(String analysisType);

    List<AnalysisResult> findByAnalysisTypeAndPeriod(String analysisType, String period);
}
