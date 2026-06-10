package com.example.Nosql_Homework.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "analysis_results")
public class AnalysisResult {

    @Id
    private String id;

    @Field("analysis_type")
    private String analysisType;

    private String period;

    @Field("analysis_data")
    private Object analysisData;

    private String version;

    @Field("data_window")
    private Object dataWindow;

    @Field("created_at")
    private Date createdAt;

    private Double accuracy;
}
