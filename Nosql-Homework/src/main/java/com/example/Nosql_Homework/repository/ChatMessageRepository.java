package com.example.Nosql_Homework.repository;

import com.example.Nosql_Homework.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Tiger-RAG 对话消息 Repository
 */
@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /** 按 sessionId 取某会话的所有消息 (按时间升序) */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /** 取某会话最新 N 条 (用于上下文) */
    Page<ChatMessage> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /** 删除会话 (清空历史) */
    void deleteBySessionId(String sessionId);
}
