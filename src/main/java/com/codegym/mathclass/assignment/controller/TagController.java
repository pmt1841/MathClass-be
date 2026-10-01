package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.response.TagResponse;
import com.codegym.mathclass.assignment.entity.TagType;
import com.codegym.mathclass.assignment.service.TagService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tags", description = "APIs quản lý và tra cứu thẻ phân loại bài tập (khối lớp, môn học, độ khó)")
@RestController
@ApiVersion(1)
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "Tra cứu danh sách thẻ", description = "Lấy danh sách thẻ đang kích hoạt theo loại hoặc tìm kiếm theo từ khóa")
    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags(
            @RequestParam(required = false) TagType type,
            @RequestParam(required = false) String query) {
        if (query != null) {
            return ResponseEntity.ok(tagService.searchTags(query));
        }
        return ResponseEntity.ok(tagService.getActiveTags(type));
    }
}
