package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.dto.SearchRequest;
import com.example.Nosql_Homework.dto.SearchResponse;

public interface SemanticSearchService {

    /**
     * 自然语言语义检索
     * 链路: 查询 → LLM 意图解析 → MongoDB 查询 → 结果返回
     */
    SearchResponse search(SearchRequest request);
}
