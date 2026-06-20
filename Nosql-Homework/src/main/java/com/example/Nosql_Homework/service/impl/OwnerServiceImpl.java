package com.example.Nosql_Homework.service.impl;

import com.example.Nosql_Homework.entity.Owner;
import com.example.Nosql_Homework.repository.OwnerRepository;
import com.example.Nosql_Homework.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OwnerServiceImpl implements OwnerService {

    private final OwnerRepository ownerRepository;

    @Override
    public Optional<Owner> getById(String id) {
        return ownerRepository.findById(id);
    }

    @Override
    public Optional<Owner> getByLogin(String login) {
        return ownerRepository.findByLogin(login);
    }

    @Override
    public List<Owner> listByType(String type) {
        return ownerRepository.findByType(type);
    }

    @Override
    public List<Owner> listTopActive() {
        return ownerRepository.findAllByOrderByActivityScoreDesc();
    }

    @Override
    public Owner save(Owner owner) {
        return ownerRepository.save(owner);
    }
}
