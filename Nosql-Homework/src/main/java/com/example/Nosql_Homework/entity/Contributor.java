package com.example.Nosql_Homework.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "contributors")
@CompoundIndex(def = "{'project_id': 1, 'contributions': -1}")
public class Contributor {

    @Id
    private String id;

    @Field("project_id")
    private String projectId;

    @Field("github_id")
    private Long githubId;

    private String login;

    @Field("avatar_url")
    private String avatarUrl;

    private Integer contributions;
}
