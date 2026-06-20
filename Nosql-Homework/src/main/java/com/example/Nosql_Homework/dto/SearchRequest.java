package com.example.Nosql_Homework.dto;

import lombok.Data;

@Data
public class SearchRequest {

    /** 自然语言查询，例如 "找 Java 语言的高星标机器学习项目" */
    private String query;

    /** 搜索结果数量 */
    private Integer topK = 10;
}
