package com.example.Nosql_Homework.controller;

import com.example.Nosql_Homework.common.R;
import com.example.Nosql_Homework.entity.Owner;
import com.example.Nosql_Homework.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @GetMapping("/{id}")
    public R<Owner> getById(@PathVariable String id) {
        return ownerService.getById(id)
                .map(R::ok)
                .orElse(R.fail(404, "所有者不存在"));
    }

    @GetMapping("/type/{type}")
    public R<List<Owner>> listByType(@PathVariable String type) {
        return R.ok(ownerService.listByType(type));
    }

    @GetMapping("/top-active")
    public R<List<Owner>> topActive() {
        return R.ok(ownerService.listTopActive());
    }

    @PostMapping
    public R<Owner> create(@RequestBody Owner owner) {
        return R.ok(ownerService.save(owner));
    }
}
