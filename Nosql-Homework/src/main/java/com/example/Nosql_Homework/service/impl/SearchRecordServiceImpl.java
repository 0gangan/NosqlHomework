package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.entity.SearchRecord;
import com.example.Nosql_Homework.repository.SearchRecordRepository;
import com.example.Nosql_Homework.service.SearchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchRecordServiceImpl implements SearchRecordService {

    private final SearchRecordRepository searchRecordRepository;

    @Override
    public SearchRecord save(SearchRecord record) {
        return searchRecordRepository.save(record);
    }

    @Override
    public PageResult<SearchRecord> listHistory(Pageable pageable) {
        Page<SearchRecord> page = searchRecordRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResult.<SearchRecord>builder()
                .total(page.getTotalElements())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .records(page.getContent())
                .build();
    }
}
