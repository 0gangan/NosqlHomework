package com.example.Nosql_Homework.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * 知识文档 POJO
 * 由离线分析任务 (MongoDB 聚合) 生成，向量化后存入知识库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    /** 文档唯一 ID */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** 文档类型: monthly_trend | language_rank | category_trend | project_analysis */
    private String type;

    /** 时间区间: 2026-03 (monthly) / 2026-Q1 (quarterly) */
    private String period;

    /** 标题 */
    private String title;

    /** 正文内容 — 会被向量化用于语义检索 */
    private String content;

    /** 结构化元数据: 语言/品类/排行等，用于精确过滤 */
    private Map<String, Object> metadata;

    /** 向量 (384维)，入库时填充 */
    private float[] embedding;

    /** 创建时间戳 */
    @Builder.Default
    private long createdAt = System.currentTimeMillis();
}
