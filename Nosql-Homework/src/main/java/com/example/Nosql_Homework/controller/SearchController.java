package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.dto.SearchRequest;
import com.example.Nosql_Homework.dto.SearchResponse;
import com.example.Nosql_Homework.entity.SearchRecord;
import com.example.Nosql_Homework.service.SearchRecordService;
import com.example.Nosql_Homework.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SemanticSearchService semanticSearchService;
    private final SearchRecordService searchRecordService;

    /**
     * 自然语言检索
     * 示例: POST /api/search/nl
     * { "query": "找 Java 语言的高星标 Web 项目", "topK": 10 }
     */
    @PostMapping("/nl")
    public R<SearchResponse> naturalLanguageSearch(@RequestBody SearchRequest request) {
        SearchResponse result = semanticSearchService.search(request);

        SearchRecord record = SearchRecord.builder()
                .query(request.getQuery())
                .queryType("natural_language")
                .intent(result.getParsedIntent())
                .results(List.of(result.getItems().toArray()))
                .matchScore(result.getItems().isEmpty() ? 0.0 : result.getItems().get(0).getMatchScore())
                .validated(false)
                .createdAt(new Date())
                .build();
        searchRecordService.save(record);

        return R.ok(result);
    }

    @GetMapping("/history")
    public R<PageResult<SearchRecord>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page - 1, size);
        return R.ok(searchRecordService.listHistory(pageable));
    }
}
