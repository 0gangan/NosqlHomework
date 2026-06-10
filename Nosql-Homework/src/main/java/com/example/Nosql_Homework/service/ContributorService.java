package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.entity.Contributor;

import java.util.List;

public interface ContributorService {

    List<Contributor> listByProject(String projectId);
}
