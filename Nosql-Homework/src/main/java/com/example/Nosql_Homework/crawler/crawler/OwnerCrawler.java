package com.example.Nosql_Homework.crawler.crawler;

import com.example.Nosql_Homework.entity.Owner;
import com.example.Nosql_Homework.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * Owner 集合爬虫 — 负责 Owner 的幂等保存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerCrawler {

    private final OwnerRepository ownerRepository;

    /**
     * 按 githubId 幂等保存 Owner (已存在则更新)
     */
    public Owner saveOwner(Map<String, Object> ownerMap) {
        long githubId = ((Number) ownerMap.get("id")).longValue();

        Owner owner = ownerRepository.findByGithubId(githubId).orElseGet(() -> Owner.builder()
                .githubId(githubId)
                .login((String) ownerMap.get("login"))
                .type((String) ownerMap.get("type"))
                .avatarUrl((String) ownerMap.get("avatar_url"))
                .htmlUrl((String) ownerMap.get("html_url"))
                .createdAt(new Date())
                .build());

        owner.setUpdatedAt(new Date());
        owner = ownerRepository.save(owner);
        log.debug("  Owner 已保存: login={}, githubId={}", owner.getLogin(), githubId);
        return owner;
    }
}
