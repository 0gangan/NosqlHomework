package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.entity.Contributor;
import com.example.Nosql_Homework.repository.ContributorRepository;
import com.example.Nosql_Homework.service.ContributorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContributorServiceImpl implements ContributorService {

    private final ContributorRepository contributorRepository;

    @Override
    public List<Contributor> listByProject(String projectId) {
        return contributorRepository.findByProjectIdOrderByContributionsDesc(projectId);
    }
}
