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
@Document(collection = "search_records")
public class SearchRecord {

    @Id
    private String id;

    private String query;

    @Field("query_type")
    private String queryType;

    private String intent;

    @Field("parsed_filters")
    private Object parsedFilters;

    private List<Object> results;

    @Field("match_score")
    private Double matchScore;

    private Boolean validated;

    @Field("validation_result")
    private Object validationResult;

    @Indexed
    @Field("created_at")
    private Date createdAt;
}
