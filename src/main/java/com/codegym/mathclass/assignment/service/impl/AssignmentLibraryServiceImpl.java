package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.assignment.mapper.AssignmentSheetMapper;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetSpecification;
import com.codegym.mathclass.assignment.service.AssignmentLibraryService;
import com.codegym.mathclass.assignment.service.helper.AssignmentCloneHelper;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentLibraryServiceImpl implements AssignmentLibraryService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSheetRepository assignmentSheetRepository;
    private final UserRepository userRepository;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentSheetMapper assignmentSheetMapper;
    private final AssignmentCloneHelper assignmentCloneHelper;

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getPublicAssignments(String keyword, Pageable pageable) {
        Specification<Assignment> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("visibility"), AssignmentVisibility.PUBLIC),
                cb.isNull(root.get("classroom")),
                cb.notEqual(root.get("status"), AssignmentStatus.DELETED));

        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
        }

        Page<Assignment> assignments = assignmentRepository.findAll(spec, pageable);
        return assignments.map(assignmentMapper::toAssignmentResponseWithoutContent);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse getPublicAssignmentDetail(long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập công khai"));

        if (assignment.getStatus() == AssignmentStatus.DELETED
                || assignment.getVisibility() != AssignmentVisibility.PUBLIC
                || assignment.getClassroom() != null) {
            throw new ResourceNotFoundException("Không tìm thấy bài tập công khai");
        }

        return assignmentMapper.toAssignmentResponse(assignment);
    }

    @Override
    @Transactional
    public AssignmentResponse cloneAssignmentFromLibrary(long assignmentId, long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        Assignment original = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (original.getVisibility() != AssignmentVisibility.PUBLIC
                || original.getStatus() == AssignmentStatus.DELETED) {
            throw new BadRequestException("Bài tập này không ở trạng thái công khai trong Thư viện");
        }

        Assignment clone = assignmentCloneHelper.cloneFromLibrary(original, teacher);
        Assignment saved = assignmentRepository.save(clone);
        return assignmentMapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable) {
        Specification<AssignmentSheet> spec = Specification.<AssignmentSheet>where((root, query, cb) -> cb.and(
                cb.equal(root.get("visibility"), AssignmentVisibility.PUBLIC),
                cb.isNull(root.get("classroom")))).and(AssignmentSheetSpecification.buildKeywordSpec(keyword));

        Page<AssignmentSheet> sheets = assignmentSheetRepository.findAll(spec, pageable);
        return sheets.map(sheet -> {
            AssignmentSheetResponse res = assignmentSheetMapper.toResponse(sheet);
            if ((res.getItems() == null || res.getItems().isEmpty())
                    && sheet.getItems() != null && !sheet.getItems().isEmpty()) {
                res.setItems(sheet.getItems().stream()
                        .filter(asgn -> asgn != null && asgn.getStatus() != AssignmentStatus.DELETED)
                        .map(asgn -> {
                            AssignmentResponse ar = assignmentMapper.toAssignmentResponseWithoutContent(asgn);
                            ar.setMaxScore(asgn.getMaxScore() != null ? asgn.getMaxScore() : 10.0);
                            return ar;
                        })
                        .toList());
            }
            return res;
        });
    }

    @Override
    @Transactional
    public AssignmentSheetResponse cloneAssignmentSheetFromLibrary(long sheetId, long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        AssignmentSheet originalSheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (originalSheet.getVisibility() != AssignmentVisibility.PUBLIC) {
            throw new BadRequestException("Phiếu bài tập này không ở trạng thái công khai trong Thư viện");
        }

        User originalAuthor = originalSheet.getOriginalAuthor() != null
                ? originalSheet.getOriginalAuthor()
                : originalSheet.getTeacher();

        AssignmentSheet clonedSheet = buildLibraryCloneSheet(originalSheet, teacher, originalAuthor);
        clonedSheet = assignmentSheetRepository.save(clonedSheet);

        if (originalSheet.getItems() != null) {
            cloneLibrarySheetItems(originalSheet.getItems(), clonedSheet, teacher, originalAuthor);
        }

        return assignmentSheetMapper.toResponse(clonedSheet);
    }

    private AssignmentSheet buildLibraryCloneSheet(
            AssignmentSheet original, User teacher, User originalAuthor) {
        return AssignmentSheet.builder()
                .title(original.getTitle())
                .description(original.getDescription())
                .teacher(teacher)
                .originalAuthor(originalAuthor)
                .visibility(AssignmentVisibility.PRIVATE)
                .classroom(null)
                .masterSheet(original)
                .build();
    }

    private void cloneLibrarySheetItems(
            List<Assignment> sourceItems, AssignmentSheet clonedSheet,
            User teacher, User originalAuthor) {

        List<Assignment> clonedAssignments = sourceItems.stream()
                .filter(asgn -> asgn != null && asgn.getStatus() != AssignmentStatus.DELETED)
                .map(asgn -> {
                    Assignment clone = assignmentCloneHelper.cloneSheetItemFromLibrary(asgn, teacher, originalAuthor);
                    clone.setAssignmentSheet(clonedSheet);
                    return clone;
                })
                .toList();

        List<Assignment> savedClones = assignmentRepository.saveAll(clonedAssignments);
        clonedSheet.getItems().addAll(savedClones);
    }
}
