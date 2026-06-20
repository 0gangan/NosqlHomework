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

/**
 * Tiger-RAG 对话消息
 * 存储每次用户提问和 AI 的回答，用于多轮对话上下文参考
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
public class ChatMessage {

    @Id
    private String id;

    /** 会话 id (多轮对话共享同一个 session) */
    @Field("session_id")
    @Indexed
    private String sessionId;

    /** 角色: user / assistant / system */
    @Field("role")
    @Indexed
    private String role;

    /** 文本内容 */
    @Field("content")
    private String content;

    /** 如果 role=assistant，这里存放本次回答引用到的知识库文档 id 列表 (用于展示"引用") */
    @Field("ref_doc_ids")
    private List<String> refDocIds;

    /** 如果 role=assistant，这里存放本次回答中检索到的文档 (原始标题+片段)，方便前端展示 */
    @Field("ref_docs_preview")
    private List<RefDocPreview> refDocsPreview;

    /** 时间戳 */
    @Field("created_at")
    @Indexed
    private Date createdAt;

    /** 回答耗时 (毫秒)，仅 assistant 消息有值 */
    @Field("duration_ms")
    private Long durationMs;

    /**
     * 引用文档的预览信息 (嵌套对象)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefDocPreview {
        /** 知识库文档 id */
        private String docId;
        /** 文档标题 */
        private String title;
        /** 分类 */
        private String category;
        /** 相似度分数 0~1 */
        private Double score;
        /** 内容片段 (前 200 字) */
        private String snippet;
    }
}
