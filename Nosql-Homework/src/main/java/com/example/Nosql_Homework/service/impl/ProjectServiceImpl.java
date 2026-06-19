package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.entity.Project;
import com.example.Nosql_Homework.repository.ProjectRepository;
import com.example.Nosql_Homework.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final EmbeddingService embeddingService;
    private final MongoTemplate mongoTemplate;

    @Override
    public PageResult<Project> listProjects(String language, String category, Integer minStars, Pageable pageable) {
        Page<Project> page;
        if (language != null && minStars != null) {
            page = projectRepository.findByLanguageAndStarsCountGreaterThan(language, minStars, pageable);
        } else if (language != null) {
            page = projectRepository.findByLanguage(language, pageable);
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

    // ============ Tiger-RAG 向量补全 ============

    @Override
    public boolean embedProject(String projectId, boolean force) {
        Optional<Project> opt = projectRepository.findById(projectId);
        if (opt.isEmpty()) {
            log.warn("[Tiger-RAG] embedProject: 项目 {} 不存在", projectId);
            return false;
        }
        Project p = opt.get();
        if (!force && Boolean.TRUE.equals(p.getHasEmbedding()) && p.getEmbedding() != null && !p.getEmbedding().isEmpty()) {
            log.info("[Tiger-RAG] embedProject: 项目 {} ({}) 已有 embedding, 跳过 (force=false)", p.getId(), p.getName());
            return true;
        }
        try {
            String text = p.toEmbeddingText();
            List<Float> vec = embeddingService.embed(text);
            if (vec == null || vec.isEmpty()) {
                log.warn("[Tiger-RAG] embedProject: 项目 {} 返回空向量, 跳过", p.getName());
                return false;
            }
            p.setEmbedding(vec);
            p.setEmbeddingModel(embeddingService.getModelName());
            p.setHasEmbedding(true);
            projectRepository.save(p);
            log.info("[Tiger-RAG] embedProject: 项目 {} ({}) 向量生成成功, 维度={}", p.getId(), p.getName(), vec.size());
            return true;
        } catch (Exception e) {
            log.error("[Tiger-RAG] embedProject: 项目 {} 异常: {} - {}", p.getName(), e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> startBatchEmbed(int batchSize, boolean force) {
        Map<String, Object> stats = embedStats();
        long toProcess;
        if (force) {
            toProcess = ((Number) stats.get("total")).longValue();
        } else {
            toProcess = ((Number) stats.get("noEmbedding")).longValue();
        }
        final long finalToProcess = toProcess;
        final int bs = Math.max(1, Math.min(500, batchSize));
        log.info("[Tiger-RAG] startBatchEmbed: force={}, batchSize={}, 将处理 {} 个项目", force, bs, finalToProcess);

        new Thread(() -> {
            long start = System.currentTimeMillis();
            long processed = 0;
            long success = 0;
            long failed = 0;
            try {
                final int pageSize = 50;
                long skip = 0;
                while (true) {
                    Query q = new Query();
                    if (!force) {
                        q.addCriteria(new Criteria().orOperator(
                                Criteria.where("hasEmbedding").is(false),
                                Criteria.where("hasEmbedding").exists(false),
                                Criteria.where("embedding").exists(false)
                        ));
                    }
                    q.with(Sort.by(Sort.Direction.ASC, "_id"));
                    q.skip((int) skip).limit(pageSize);
                    List<Project> projects = mongoTemplate.find(q, Project.class);
                    if (projects == null || projects.isEmpty()) break;

                    for (Project p : projects) {
                        try {
                            // 即使 force=true，也做一次简单重复判断，避免重复调用 API
                            if (!force || p.getEmbedding() == null || p.getEmbedding().isEmpty()) {
                                String text = p.toEmbeddingText();
                                List<Float> vec = embeddingService.embed(text);
                                if (vec != null && !vec.isEmpty()) {
                                    p.setEmbedding(vec);
                                    p.setEmbeddingModel(embeddingService.getModelName());
                                    p.setHasEmbedding(true);
                                    projectRepository.save(p);
                                    success++;
                                } else {
                                    failed++;
                                }
                            }
                        } catch (Exception ex) {
                            failed++;
                            log.warn("[Tiger-RAG] batch-embed 项目 {} 失败: {} - {}",
                                    p.getName(), ex.getClass().getSimpleName(), ex.getMessage());
                        }
                        processed++;
                        if (processed % bs == 0) {
                            log.info("[Tiger-RAG] batch-embed 进度 {}/{}, 成功={}, 失败={}",
                                    processed, finalToProcess, success, failed);
                        }
                    }
                    if (projects.size() < pageSize) break;
                    skip += pageSize;
                }
            } catch (Exception e) {
                log.error("[Tiger-RAG] batch-embed 主循环异常: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            }
            long cost = System.currentTimeMillis() - start;
            log.info("[Tiger-RAG] batch-embed 完成: 处理={}, 成功={}, 失败={}, 耗时={}ms",
                    processed, success, failed, cost);
        }, "tiger-rag-batch-embed").start();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "批量向量化任务已在后台启动");
        res.put("force", force);
        res.put("batchSize", bs);
        res.put("willProcess", finalToProcess);
        res.put("stats", stats);
        return res;
    }

    @Override
    public Map<String, Object> embedStats() {
        Map<String, Object> res = new LinkedHashMap<>();
        long total = projectRepository.count();
        long noEmbed = projectRepository.countByHasEmbeddingFalseOrHasEmbeddingIsNull();
        // 兜底：用 MongoTemplate 直接按字段是否存在再统计一次，避免旧数据中 hasEmbedding=null 且 embedding=null 统计不准
        long missingEmbeddingField = mongoTemplate.count(
                Query.query(new Criteria().orOperator(
                        Criteria.where("embedding").exists(false),
                        Criteria.where("embedding").is(null)
                )),
                Project.class
        );
        long reallyEmpty = Math.max(noEmbed, missingEmbeddingField);
        long withEmbedding = total - reallyEmpty;
        res.put("total", total);
        res.put("withEmbedding", withEmbedding);
        res.put("noEmbedding", reallyEmpty);
        res.put("embeddingModel", embeddingService.getModelName());
        return res;
    }
}
