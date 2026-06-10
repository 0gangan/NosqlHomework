package com.example.Nosql_Homework.service;

import com.example.Nosql_Homework.entity.Owner;

import java.util.List;
import java.util.Optional;

public interface OwnerService {

    Optional<Owner> getById(String id);

    Optional<Owner> getByLogin(String login);

    List<Owner> listByType(String type);

    List<Owner> listTopActive();

    Owner save(Owner owner);
}
