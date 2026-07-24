package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentImage;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentSheetItem;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetItemRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.entity.SubmissionStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
public class AssignmentSheetServiceImpl implements AssignmentSheetService {

    private final AssignmentSheetRepository assignmentSheetRepository;
    private final AssignmentSheetItemRepository assignmentSheetItemRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional
    public void publishAssignmentSheet(PublishAssignmentSheetRequest request, long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên"));

        List<Assignment> originalAssignments = new ArrayList<>();
        if (request.getAssignmentIds() != null && !request.getAssignmentIds().isEmpty()) {
            originalAssignments = assignmentRepository.findAllById(request.getAssignmentIds());
        }

        // Tự động tìm lại bài tập thuộc phiếu nếu assignmentIds rỗng (khi giao lại phiếu)
        if (originalAssignments.isEmpty()) {
            List<AssignmentSheet> sameTitleSheets = assignmentSheetRepository.findByTeacherIdAndTitle(teacherId, request.getTitle());
            for (AssignmentSheet s : sameTitleSheets) {
                if (s.getItems() != null && !s.getItems().isEmpty()) {
                    for (AssignmentSheetItem item : s.getItems()) {
                        Assignment asgn = item.getAssignment();
                        if (asgn != null) {
                            if (asgn.getParentId() != null) {
                                assignmentRepository.findById(asgn.getParentId()).ifPresent(originalAssignments::add);
                            } else {
                                originalAssignments.add(asgn);
                            }
                        }
                    }
                    if (!originalAssignments.isEmpty()) break;
                }
            }
        }

        if (originalAssignments.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy bài tập nào để giao.");
        }

        // 1. Tạo hoặc lưu Bản chính (Master Sheet) trong Kho bài tập (classroom = null)
        AssignmentSheet masterSheet = assignmentSheetRepository
                .findFirstByTeacherIdAndTitleAndClassroomIsNull(teacherId, request.getTitle())
                .orElseGet(() -> {
                    AssignmentSheet sheet = new AssignmentSheet();
                    sheet.setTitle(request.getTitle());
                    sheet.setDescription(request.getDescription());
                    if (request.getVisibility() != null) {
                        sheet.setVisibility(request.getVisibility());
                    }
                    sheet.setTeacher(teacher);
                    sheet.setClassroom(null);
                    return assignmentSheetRepository.save(sheet);
                });

        // Tạo clone bài tập lẻ cho Master Sheet nếu masterSheet chưa có items hoặc items chưa trỏ tới clone
        boolean masterHasClones = masterSheet.getItems() != null && !masterSheet.getItems().isEmpty()
                && masterSheet.getItems().stream().allMatch(item -> item.getAssignment() != null && item.getAssignment().getParentId() != null);

        if (!masterHasClones) {
            if (masterSheet.getItems() != null && !masterSheet.getItems().isEmpty()) {
                assignmentSheetItemRepository.deleteAll(masterSheet.getItems());
                masterSheet.getItems().clear();
            }

            for (Assignment original : originalAssignments) {
                Assignment masterClone = createAssignmentClone(original, teacher, null, null);

                AssignmentSheetItem item = new AssignmentSheetItem();
                item.setSheet(masterSheet);
                item.setAssignment(masterClone);
                assignmentSheetItemRepository.save(item);

                if (original.getStatus() == AssignmentStatus.DRAFT) {
                    original.setStatus(AssignmentStatus.ARCHIVED);
                    assignmentRepository.save(original);
                }
            }
        }

        // 2. Clone phiếu bài tập cho từng lớp được chọn (nếu targets không rỗng)
        if (request.getTargets() != null) {
            for (PublishAssignmentSheetRequest.TargetClass target : request.getTargets()) {
                Classroom classroom = classroomRepository.findByClassCode(target.getClassCode())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học: " + target.getClassCode()));

                // Tạo Sheet Clone cho lớp
                AssignmentSheet clonedSheet = new AssignmentSheet();
                clonedSheet.setTitle(request.getTitle());
                clonedSheet.setDescription(request.getDescription());
                clonedSheet.setDeadline(target.getDeadline());
                clonedSheet.setTeacher(teacher);
                clonedSheet.setClassroom(classroom);
                clonedSheet = assignmentSheetRepository.save(clonedSheet);

                // Clone các bài tập và thêm vào sheet items của lớp
                for (Assignment original : originalAssignments) {
                    Assignment clone = createAssignmentClone(original, teacher, classroom, target.getDeadline());

                    AssignmentSheetItem item = new AssignmentSheetItem();
                    item.setSheet(clonedSheet);
                    item.setAssignment(clone);
                    assignmentSheetItemRepository.save(item);

                    // Update original status to ARCHIVED if it was DRAFT
                    if (original.getStatus() == AssignmentStatus.DRAFT) {
                        original.setStatus(AssignmentStatus.ARCHIVED);
                        assignmentRepository.save(original);
                    }
                }
            }
        }
    }

    private Assignment createAssignmentClone(Assignment original, User teacher, Classroom classroom, LocalDateTime deadline) {
        Assignment clone = new Assignment();
        clone.setTitle(original.getTitle());
        clone.setDescription(original.getDescription());
        clone.setContent(original.getContent());
        clone.setDeadline(deadline);
        clone.setStatus(AssignmentStatus.PUBLISHED);
        clone.setTeacher(teacher);
        clone.setParentId(original.getId());
        clone.setClassroom(classroom);

        if (original.getDrawings() != null) {
            for (AssignmentDrawing originalDrawing : original.getDrawings()) {
                AssignmentDrawing drawing = new AssignmentDrawing();
                drawing.setShapeCode(originalDrawing.getShapeCode());
                drawing.setJsxGraphData(originalDrawing.getJsxGraphData());
                drawing.setAssignment(clone);
                clone.getDrawings().add(drawing);
            }
        }

        if (original.getImages() != null) {
            for (AssignmentImage originalImage : original.getImages()) {
                AssignmentImage image = new AssignmentImage();
                image.setImageCode(originalImage.getImageCode());
                image.setImageUrl(originalImage.getImageUrl());
                image.setAssignment(clone);
                clone.getImages().add(image);
            }
        }

        return assignmentRepository.save(clone);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getAssignmentSheetsForCurrentUser(long userId, String role, String keyword, String classCode, Pageable pageable) {
        Specification<AssignmentSheet> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (Role.TEACHER.name().equals(role)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("teacher").get("id"), userId));
            // Nếu ở Kho bài tập (classCode rỗng), chỉ lấy Bản chính (classroom IS NULL)
            if (classCode == null || classCode.trim().isEmpty()) {
                spec = spec.and((root, query, cb) -> cb.isNull(root.get("classroom")));
            }
        } else if (Role.STUDENT.name().equals(role)) {
            spec = spec.and((root, query, cb) -> {
                Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
                Join<Classroom, User> studentsJoin = classroomJoin.join("students", JoinType.INNER);
                return cb.equal(studentsJoin.get("id"), userId);
            });
        } else {
            throw new AccessDeniedException("Role không hợp lệ");
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
        }

        if (classCode != null && !classCode.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
                return cb.equal(classroomJoin.get("classCode"), classCode);
            });
        }

        Page<AssignmentSheet> sheets = assignmentSheetRepository.findAll(spec, pageable);
        
        return sheets.map(sheet -> {
            AssignmentSheetResponse res = AssignmentSheetResponse.fromEntity(sheet);
            if ((res.getItems() == null || res.getItems().isEmpty()) && Role.TEACHER.name().equals(role)) {
                List<AssignmentSheet> sameTitleSheets = assignmentSheetRepository.findByTeacherIdAndTitle(userId, sheet.getTitle());
                for (AssignmentSheet s : sameTitleSheets) {
                    if (s.getItems() != null && !s.getItems().isEmpty()) {
                        res.setItems(s.getItems().stream()
                            .map(item -> AssignmentResponse.fromEntityWithoutContent(item.getAssignment()))
                            .collect(java.util.stream.Collectors.toList()));
                        break;
                    }
                }
            }
            if (Role.STUDENT.name().equals(role) && res.getItems() != null) {
                boolean allSubmittedOrGraded = true;
                boolean anyGraded = false;
                
                for (AssignmentResponse item : res.getItems()) {
                    var subOpt = submissionRepository.findFirstByAssignmentIdAndStudentId(item.getId(), userId);
                    if (subOpt.isPresent()) {
                        var status = subOpt.get().getStatus();
                        item.setSubmissionStatus(status.name());
                        item.setSubmissionCreatedAt(subOpt.get().getCreatedAt());
                        item.setSubmissionUpdatedAt(subOpt.get().getUpdatedAt());
                        if (status == SubmissionStatus.DRAFT) {
                            allSubmittedOrGraded = false;
                        }
                        if (status == SubmissionStatus.GRADED) {
                            anyGraded = true;
                        }
                    } else {
                        allSubmittedOrGraded = false;
                    }
                }
                
                if (res.getItems().isEmpty()) {
                    res.setSubmissionStatus(null);
                } else if (allSubmittedOrGraded) {
                    res.setSubmissionStatus(anyGraded ? "GRADED" : "SUBMITTED");
                } else {
                    res.setSubmissionStatus(null);
                }
            } else if (Role.TEACHER.name().equals(role)) {
                res.setHasSubmissions(true); // Sheets are always published
                List<AssignmentSheet> allTeacherSheets = assignmentSheetRepository.findAll((root, query, cb) -> 
                    cb.and(
                        cb.equal(root.get("teacher").get("id"), userId),
                        cb.equal(root.get("title"), sheet.getTitle())
                    )
                );
                List<String> publishedCodes = allTeacherSheets.stream()
                        .filter(s -> s.getClassroom() != null)
                        .map(s -> s.getClassroom().getClassCode())
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
                res.setPublishedClassCodes(publishedCodes);
            }
            return res;
        });
    }

    @Override
    @Transactional
    public void deleteAssignmentSheet(long sheetId, long teacherId) {
        AssignmentSheet sheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (sheet.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xóa phiếu bài tập này");
        }

        assignmentSheetRepository.delete(sheet);
    }

    @Override
    @Transactional
    public AssignmentSheetResponse updateAssignmentSheet(long sheetId, UpdateAssignmentSheetRequest request, long teacherId) {
        AssignmentSheet sheet = assignmentSheetRepository.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bài tập"));

        if (sheet.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền sửa phiếu bài tập này");
        }

        String oldTitle = sheet.getTitle();
        sheet.setTitle(request.getTitle());
        sheet.setDescription(request.getDescription() != null ? request.getDescription() : "");
        if (request.getVisibility() != null) {
            sheet.setVisibility(request.getVisibility());
        }
        sheet = assignmentSheetRepository.save(sheet);

        if (oldTitle != null && !oldTitle.equals(request.getTitle())) {
            List<AssignmentSheet> relatedSheets = assignmentSheetRepository.findByTeacherIdAndTitle(teacherId, oldTitle);
            for (AssignmentSheet related : relatedSheets) {
                related.setTitle(request.getTitle());
                related.setDescription(request.getDescription() != null ? request.getDescription() : "");
                if (request.getVisibility() != null) {
                    related.setVisibility(request.getVisibility());
                }
            }
            assignmentSheetRepository.saveAll(relatedSheets);
        } else {
            List<AssignmentSheet> relatedSheets = assignmentSheetRepository.findByTeacherIdAndTitle(teacherId, sheet.getTitle());
            for (AssignmentSheet related : relatedSheets) {
                related.setDescription(request.getDescription() != null ? request.getDescription() : "");
                if (request.getVisibility() != null) {
                    related.setVisibility(request.getVisibility());
                }
            }
            assignmentSheetRepository.saveAll(relatedSheets);
        }

        return AssignmentSheetResponse.fromEntity(sheet);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable) {
        Specification<AssignmentSheet> spec = Specification.where((root, query, cb) -> cb.and(
                cb.equal(root.get("visibility"), com.codegym.mathclass.assignment.entity.AssignmentVisibility.PUBLIC),
                cb.isNull(root.get("classroom"))
        ));

        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
        }

        Page<AssignmentSheet> sheets = assignmentSheetRepository.findAll(spec, pageable);
        return sheets.map(sheet -> {
            AssignmentSheetResponse res = AssignmentSheetResponse.fromEntity(sheet);
            if (res.getItems() == null || res.getItems().isEmpty()) {
                if (sheet.getItems() != null && !sheet.getItems().isEmpty()) {
                    res.setItems(sheet.getItems().stream()
                        .filter(item -> item.getAssignment() != null && item.getAssignment().getStatus() != com.codegym.mathclass.assignment.entity.AssignmentStatus.DELETED)
                        .map(item -> AssignmentResponse.fromEntityWithoutContent(item.getAssignment()))
                        .collect(Collectors.toList()));
                }
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

        if (originalSheet.getVisibility() != com.codegym.mathclass.assignment.entity.AssignmentVisibility.PUBLIC) {
            throw new com.codegym.mathclass.exception.BadRequestException("Phiếu bài tập này không ở trạng thái công khai trong Thư viện");
        }

        User originalAuthor = originalSheet.getOriginalAuthor() != null ? originalSheet.getOriginalAuthor() : originalSheet.getTeacher();

        AssignmentSheet clonedSheet = new AssignmentSheet();
        clonedSheet.setTitle(originalSheet.getTitle());
        clonedSheet.setDescription(originalSheet.getDescription());
        clonedSheet.setTeacher(teacher);
        clonedSheet.setOriginalAuthor(originalAuthor);
        clonedSheet.setVisibility(com.codegym.mathclass.assignment.entity.AssignmentVisibility.PRIVATE);
        clonedSheet.setClassroom(null);
        clonedSheet = assignmentSheetRepository.save(clonedSheet);

        if (originalSheet.getItems() != null) {
            for (AssignmentSheetItem item : originalSheet.getItems()) {
                Assignment originalAsgn = item.getAssignment();
                if (originalAsgn != null && originalAsgn.getStatus() != AssignmentStatus.DELETED) {
                    Assignment clonedAsgn = createAssignmentClone(originalAsgn, teacher, null, null);
                    clonedAsgn.setOriginalAuthor(originalAuthor);
                    clonedAsgn.setStatus(AssignmentStatus.DRAFT);
                    clonedAsgn.setVisibility(com.codegym.mathclass.assignment.entity.AssignmentVisibility.PRIVATE);
                    assignmentRepository.save(clonedAsgn);

                    AssignmentSheetItem newItem = new AssignmentSheetItem();
                    newItem.setSheet(clonedSheet);
                    newItem.setAssignment(clonedAsgn);
                    assignmentSheetItemRepository.save(newItem);
                }
            }
        }

        return AssignmentSheetResponse.fromEntity(clonedSheet);
    }
}
