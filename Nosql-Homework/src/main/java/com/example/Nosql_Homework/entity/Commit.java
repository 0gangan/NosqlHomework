package com.example.Nosql_Homework.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "commits")
@CompoundIndex(def = "{'project_id': 1, 'commit_date': -1}")
public class Commit {

    @Id
    private String id;

    @Field("project_id")
    private String projectId;

    @Indexed(unique = true)
    private String sha;

    @Field("author_login")
    private String authorLogin;

    private String message;

    @Field("commit_date")
    private Date commitDate;

    private Integer additions;

    private Integer deletions;
}
