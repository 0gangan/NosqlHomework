package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.entity.Commit;
import com.example.Nosql_Homework.common.PageResult;
import org.springframework.data.domain.Pageable;

public interface CommitService {

    PageResult<Commit> listByProject(String projectId, Pageable pageable);
}
