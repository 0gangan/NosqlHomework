package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.common.PageResult;
import com.example.Nosql_Homework.entity.Commit;
import com.example.Nosql_Homework.repository.CommitRepository;
import com.example.Nosql_Homework.service.CommitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommitServiceImpl implements CommitService {

    private final CommitRepository commitRepository;

    @Override
    public PageResult<Commit> listByProject(String projectId, Pageable pageable) {
        Page<Commit> page = commitRepository.findByProjectIdOrderByCommitDateDesc(projectId, pageable);
        return PageResult.<Commit>builder()
                .total(page.getTotalElements())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .records(page.getContent())
                .build();
    }
}
