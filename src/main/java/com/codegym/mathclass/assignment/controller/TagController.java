package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.TagResponse;
import com.codegym.mathclass.assignment.entity.TagType;
import com.codegym.mathclass.assignment.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags(@RequestParam(required = false) TagType type) {
        return ResponseEntity.ok(tagService.getActiveTags(type));
    }
}
