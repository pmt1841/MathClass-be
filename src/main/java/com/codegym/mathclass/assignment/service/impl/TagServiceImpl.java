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
    public void replaceTags(Assignment assignment, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        if (tagIds.size() > TagType.values().length || tagIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(tagIds).size() != tagIds.size()) {
            throw new BadRequestException("Danh sách tag không hợp lệ");
        }
        List<Tag> tags = tagRepository.findByIdInAndActiveTrue(tagIds);
        if (tags.size() != tagIds.size() || tags.stream().map(Tag::getType).distinct().count() != tags.size()) {
            throw new BadRequestException("Tag không tồn tại, đã ngừng hoạt động hoặc bị trùng nhóm");
        }
        assignment.getAssignmentTags().clear();
        entityManager.flush();
        for (Tag tag : tags) {
            assignment.getAssignmentTags().add(AssignmentTag.builder().assignment(assignment).tag(tag).build());
        }
    }

    @Override
    public void requireCompletePublicTags(Assignment assignment) {
        Set<TagType> types = assignment.getAssignmentTags().stream().map(link -> link.getTag().getType()).collect(java.util.stream.Collectors.toSet());
        if (types.size() != TagType.values().length || !types.containsAll(Set.of(TagType.values()))) {
            throw new BadRequestException("Cần chọn Khối lớp, Phân môn và Độ khó trước khi chia sẻ vào Thư viện cộng đồng.");
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
}
