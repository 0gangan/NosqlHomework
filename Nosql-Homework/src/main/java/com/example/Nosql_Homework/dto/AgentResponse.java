package com.example.Nosql_Homework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 问答响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /** 原始问题 */
    private String question;

    /** LLM 生成的回答 */
    private String answer;

    /** 引用的知识来源 */
    private List<String> sources;

    /** 置信度 (0~1) */
    private Double confidence;

    /** 检索策略: precise(精确) | semantic(语义) | hybrid(混合) */
    private String strategy;
}
