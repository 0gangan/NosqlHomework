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
}
