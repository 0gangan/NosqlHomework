package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.crawler.util.LanguageNormalizer;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public PageResult<Project> listProjects(String language, String category, Integer minStars, Pageable pageable) {
        String normalizedLang = LanguageNormalizer.normalize(language);
        Page<Project> page;
        if (normalizedLang != null && minStars != null) {
            page = projectRepository.findByLanguageAndStarsCountGreaterThan(normalizedLang, minStars, pageable);
        } else if (normalizedLang != null) {
            page = projectRepository.findByLanguage(normalizedLang, pageable);
        } else if (category != null) {
            page = projectRepository.findByCategory(category, pageable);
        } else {
            page = projectRepository.findAll(pageable);
        }
        return PageResult.<Project>builder()
                .total(page.getTotalElements())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .records(page.getContent())
                .build();
    }

    @Override
    public Optional<Project> getById(String id) {
        return projectRepository.findById(id);
    }

    @Override
    public Optional<Project> getByFullName(String fullName) {
        return projectRepository.findByFullName(fullName);
    }

    @Override
    public Project save(Project project) {
        return projectRepository.save(project);
    }

    @Override
    public void deleteById(String id) {
        projectRepository.deleteById(id);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> getLanguageStats() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("language").count().as("count"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "count"))
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "projects", Map.class);
        return (List) results.getMappedResults();
    }
}
