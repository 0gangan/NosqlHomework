package com.example.Nosql_Homework.dto;

import com.example.Nosql_Homework.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /** 原始查询 */
    private String query;

    /** LLM 解析出的意图 */
    private String parsedIntent;

    /** 结构化过滤条件 */
    private String language;
    private String category;
    private String keywords;

    /** 检索结果 */
    private List<ProjectItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectItem {
        private String id;
        private String fullName;
        private String description;
        private String language;
        private Integer starsCount;
        private Double matchScore;

        public static ProjectItem from(Project p, double score) {
            return ProjectItem.builder()
                    .id(p.getId())
                    .fullName(p.getFullName())
                    .description(p.getDescription())
                    .language(p.getLanguage())
                    .starsCount(p.getStarsCount())
                    .matchScore(score)
                    .build();
        }
    }
}
