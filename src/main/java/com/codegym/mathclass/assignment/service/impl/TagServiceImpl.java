package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.TagResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentTag;
import com.codegym.mathclass.assignment.entity.Tag;
import com.codegym.mathclass.assignment.entity.TagType;
import com.codegym.mathclass.assignment.repository.TagRepository;
import com.codegym.mathclass.assignment.service.TagService;
import com.codegym.mathclass.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final TagRepository tagRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getActiveTags(TagType type) {
        List<Tag> tags = type == null ? tagRepository.findByActiveTrueOrderByTypeAscNameAsc()
                : tagRepository.findByActiveTrueAndTypeOrderByNameAsc(type);
        return tags.stream().map(TagResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> searchTags(String query) {
        List<Tag> tags;
        if (query == null || query.trim().isEmpty()) {
            tags = tagRepository.findTop30ByActiveTrueOrderByNameAsc();
        } else {
            tags = tagRepository.findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(query.trim());
        }
        return tags.stream().map(TagResponse::fromEntity).toList();
    }

    @Override
    public void replaceTags(Assignment assignment, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        if (tagIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(tagIds).size() != tagIds.size()) {
            throw new BadRequestException("Danh sách tag không hợp lệ");
        }
        List<Tag> tags = tagRepository.findByIdInAndActiveTrue(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new BadRequestException("Tag không tồn tại hoặc đã ngừng hoạt động");
        }
        assignment.getAssignmentTags().clear();
        entityManager.flush();
        for (Tag tag : tags) {
            assignment.getAssignmentTags().add(AssignmentTag.builder().assignment(assignment).tag(tag).build());
        }
    }

    @Override
    public void replaceTagsByName(Assignment assignment, List<String> tagNames) {
        if (tagNames == null) {
            return;
        }
        // Deduplicate input tag names case-insensitively
        java.util.Map<String, String> normalizedNamesMap = new java.util.LinkedHashMap<>();
        for (String name : tagNames) {
            if (name != null && !name.trim().isEmpty()) {
                String trimmed = name.trim();
                normalizedNamesMap.putIfAbsent(trimmed.toLowerCase(), trimmed);
            }
        }
        assignment.getAssignmentTags().clear();
        entityManager.flush();

        if (normalizedNamesMap.isEmpty()) {
            return;
        }

        // Batch query existing tags to eliminate N+1 queries
        List<Tag> existingTags = tagRepository.findByNameInIgnoreCase(normalizedNamesMap.values());
        java.util.Map<String, Tag> tagMap = new java.util.HashMap<>();
        for (Tag tag : existingTags) {
            tagMap.put(tag.getName().toLowerCase(), tag);
        }

        Set<Long> addedTagIds = new HashSet<>();
        for (String originalName : normalizedNamesMap.values()) {
            String lowerName = originalName.toLowerCase();
            Tag tag = tagMap.get(lowerName);
            if (tag == null) {
                tag = tagRepository.save(Tag.builder().name(originalName).type(TagType.CUSTOM).active(true).build());
                tagMap.put(lowerName, tag);
            }
            if (tag.isActive() && tag.getId() != 0L && addedTagIds.add(tag.getId())) {
                assignment.getAssignmentTags().add(AssignmentTag.builder().assignment(assignment).tag(tag).build());
            }
        }
    }

    @Override
    public void requireCompletePublicTags(Assignment assignment) {
        if (assignment.getAssignmentTags() == null || assignment.getAssignmentTags().isEmpty()) {
            throw new BadRequestException("Bài tập cần có ít nhất 1 tag trước khi công khai lên Thư viện cộng đồng.");
        }
    }

    @Override
    public void copyTags(Assignment source, Assignment target) {
        if (source.getAssignmentTags() == null) {
            return;
        }
        for (AssignmentTag link : source.getAssignmentTags()) {
            target.getAssignmentTags().add(AssignmentTag.builder().assignment(target).tag(link.getTag()).build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateTagFilters(Long gradeTagId, Long subjectTagId, Long difficultyTagId) {
        if (gradeTagId != null) {
            Tag tag = tagRepository.findById(gradeTagId)
                    .orElseThrow(() -> new BadRequestException("Tag khối lớp không tồn tại"));
            if (!tag.isActive()) {
                throw new BadRequestException("Tag khối lớp không hợp lệ hoặc không còn hoạt động");
            }
        }
        if (subjectTagId != null) {
            Tag tag = tagRepository.findById(subjectTagId)
                    .orElseThrow(() -> new BadRequestException("Tag phân môn không tồn tại"));
            if (!tag.isActive()) {
                throw new BadRequestException("Tag phân môn không hợp lệ hoặc không còn hoạt động");
            }
        }
        if (difficultyTagId != null) {
            Tag tag = tagRepository.findById(difficultyTagId)
                    .orElseThrow(() -> new BadRequestException("Tag độ khó không tồn tại"));
            if (!tag.isActive()) {
                throw new BadRequestException("Tag độ khó không hợp lệ hoặc không còn hoạt động");
            }
        }
    }
}
