package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.agent.AgentService;
import com.example.Nosql_Homework.agent.AnalysisTaskService;
import com.example.Nosql_Homework.agent.KnowledgeBaseService;
import com.example.Nosql_Homework.agent.KnowledgeDocument;
import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.dto.AgentRequest;
import com.example.Nosql_Homework.dto.AgentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "Agent 智能问答", description = "RAG 智能问答接口")
public class AgentController {

    private final AgentService agentService;
    private final AnalysisTaskService analysisTaskService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 智能问答
     * POST /api/agent/ask
     * { "question": "今年六月最火的项目是什么？", "topK": 3 }
     */
    @PostMapping("/ask")
    @Operation(summary = "智能问答", description = "基于 RAG 知识库的智能问答，支持趋势分析、语言排名等")
    public R<AgentResponse> ask(@RequestBody AgentRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return R.fail("问题不能为空");
        }
        AgentResponse response = agentService.ask(request);
        return R.ok(response);
    }

    /**
     * 触发分析（管理用）
     * POST /api/agent/analyze?period=2026-06
     */
    @PostMapping("/analyze")
    @Operation(summary = "触发离线分析", description = "手动触发一次知识文档生成（从 MongoDB 聚合数据）")
    public R<List<KnowledgeDocument>> triggerAnalysis(@RequestParam(defaultValue = "2026-06") String period) {
        List<KnowledgeDocument> docs = analysisTaskService.runAllTasks(period);
        return R.ok("已生成 " + docs.size() + " 条知识文档", docs);
    }

    /**
     * 查看知识库状态
     * GET /api/agent/knowledge-base
     */
    @GetMapping("/knowledge-base")
    @Operation(summary = "知识库状态", description = "查看知识库中文档数量和类型分布")
    public R<Map<String, Object>> knowledgeBaseStatus() {
        List<KnowledgeDocument> docs = knowledgeBaseService.listAll();
        long typeCount = docs.stream().map(KnowledgeDocument::getType).distinct().count();
        return R.ok(Map.of(
                "totalDocs", knowledgeBaseService.size(),
                "types", typeCount,
                "documents", docs.stream()
                        .map(d -> Map.of("id", d.getId(), "type", d.getType(),
                                "period", d.getPeriod(), "title", d.getTitle()))
                        .toList()
        ));
    }
}
