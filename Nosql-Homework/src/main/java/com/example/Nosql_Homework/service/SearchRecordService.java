package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.entity.SearchRecord;
import com.example.Nosql_Homework.common.PageResult;
import org.springframework.data.domain.Pageable;

public interface SearchRecordService {

    SearchRecord save(SearchRecord record);

    PageResult<SearchRecord> listHistory(Pageable pageable);
}
