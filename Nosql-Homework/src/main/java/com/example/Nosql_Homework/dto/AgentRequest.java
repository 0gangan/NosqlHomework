package com.example.Nosql_Homework.dto;

import lombok.Data;

/**
 * Agent 问答请求
 */
@Data
public class AgentRequest {

    /** 用户自然语言问题 */
    private String question;

    /** 检索 TopK 知识文档 */
    private Integer topK = 3;
}
