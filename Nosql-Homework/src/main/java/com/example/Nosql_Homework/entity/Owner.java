package com.example.Nosql_Homework.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "owners")
public class Owner {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("github_id")
    private Long githubId;

    private String login;

    private String type; // User / Organization

    private String name;

    @Field("avatar_url")
    private String avatarUrl;

    @Field("html_url")
    private String htmlUrl;

    @Field("repos_count")
    private Integer reposCount;

    @Field("followers_count")
    private Integer followersCount;

    @Field("members_count")
    private Integer membersCount;

    @Field("activity_score")
    private Double activityScore;

    @Field("tech_depth_score")
    private Double techDepthScore;

    @Field("main_tech_stacks")
    private List<String> mainTechStacks;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;
}
