package com.example.Nosql_Homework.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "projects")
@CompoundIndex(def = "{'language': 1, 'stars_count': -1}")
@CompoundIndex(def = "{'category': 1, 'language': 1, 'long_term_value': -1}")
public class Project {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("github_id")
    private Long githubId;

    private String name;

    @Field("full_name")
    private String fullName;

    @Field("owner_id")
    private String ownerId;

    @TextIndexed
    private String description;

    private String language;

    private List<String> topics;

    private String license;

    @Field("stars_count")
    private Integer starsCount;

    @Field("forks_count")
    private Integer forksCount;

    @Field("watchers_count")
    private Integer watchersCount;

    @Field("open_issues_count")
    private Integer openIssuesCount;

    @Field("size_kb")
    private Integer sizeKb;

    @Field("default_branch")
    private String defaultBranch;

    @Field("commits_count")
    private Integer commitsCount;

    @Field("contributors_count")
    private Integer contributorsCount;

    @Field("last_push_at")
    private Date lastPushAt;

    private String category;

    @Field("quality_score")
    private Double qualityScore;

    @Field("long_term_value")
    private Double longTermValue;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    @Indexed
    private Date updatedAt;

    @Field("crawled_at")
    private Date crawledAt;

    // ========== Tiger-RAG 向量数据库字段 (MongoDB Atlas Vector Search) ==========

    /** 内容向量（由通义千问 DashScope Embedding 生成，1024 维） */
    @Field("embedding")
    private List<Float> embedding;

    /** 生成向量时使用的 embedding 模型名，便于后续升级模型时重算 */
    @Field("embedding_model")
    private String embeddingModel;

    /** 是否已生成向量（用于增量补全时快速过滤） */
    @Field("has_embedding")
    @Indexed
    private Boolean hasEmbedding;

    /** 组装转向量用的文本（标题 + 描述 + topics + 语言 + 分类） */
    public String toEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        if (fullName != null) sb.append("项目: ").append(fullName).append("\n");
        if (name != null) sb.append("名称: ").append(name).append("\n");
        if (language != null) sb.append("编程语言: ").append(language).append("\n");
        if (category != null) sb.append("分类: ").append(category).append("\n");
        if (topics != null && !topics.isEmpty()) sb.append("标签: ").append(String.join(", ", topics)).append("\n");
        if (description != null) sb.append("描述: ").append(description).append("\n");
        if (starsCount != null) sb.append("Star 数: ").append(starsCount).append("\n");
        if (license != null) sb.append("License: ").append(license).append("\n");
        return sb.toString().trim();
    }
}
