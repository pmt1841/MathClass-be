package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.assignment.service.TagService;
import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.request.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.request.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.request.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import com.codegym.mathclass.assignment.repository.AssignmentSpecification;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import org.springframework.data.jpa.domain.Specification;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import com.codegym.mathclass.assignment.repository.AssignmentImageRepository;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.dto.response.SheetSiblingResponse;
import com.codegym.mathclass.assignment.dto.request.UpdateVisibilityRequest;

import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.assignment.entity.AssignmentImage;
import com.codegym.mathclass.storage.service.StorageService;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParserFactory;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParserStrategy;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParseResult;
import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParseResult;
import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import com.codegym.mathclass.assignment.dto.request.AssignmentDrawingRequest;
import com.codegym.mathclass.assignment.dto.request.AssignmentImageRequest;
import com.codegym.mathclass.utils.EmailService;
import org.thymeleaf.context.Context;
import org.springframework.beans.factory.annotation.Value;


@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentImageRepository assignmentImageRepository;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentMapper assignmentMapper;
    private final StorageService storageService;
    private final DocumentParserFactory documentParserFactory;
    private final EmailService emailService;
    private final TagService tagService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    @Transactional
    public AssignmentResponse createAssignment(CreateAssignmentRequest request, long teacherId) {
        // 1. Tìm giáo viên
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Chỉ giáo viên mới có quyền tạo bài tập");
        }

        // 3. Validate LaTeX trong nội dung bài tập
        if (request.getContent() != null && !LaTeXSanitizer.isSafe(request.getContent())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(request.getContent());
            throw new IllegalArgumentException(
                    "Nội dung chứa lệnh LaTeX không được phép: " + dangerous);
        }

        // 4. Tạo bài tập với trạng thái DRAFT, chưa gán lớp và chưa có deadline
        Assignment assignment = Assignment.builder()
                .title(request.getTitle() != null ? request.getTitle() : "")
                .description(request.getDescription() != null ? request.getDescription() : "")
                .content(request.getContent() != null ? request.getContent() : "")
                .status(AssignmentStatus.DRAFT)
                .visibility(request.getVisibility() != null ? request.getVisibility() : AssignmentVisibility.PRIVATE)
                .allowResubmit(request.getAllowResubmit() != null ? request.getAllowResubmit() : false)
                .teacher(teacher)
                .classroom(null)
                .build();
        // deadline = null cho đến khi giáo viên publish

        updateDrawings(assignment, request.getDrawings());
        updateImages(assignment, request.getImages());
        if (request.getTagNames() != null) {
            tagService.replaceTagsByName(assignment, request.getTagNames());
        } else {
            tagService.replaceTags(assignment, request.getTagIds());
        }
        if (assignment.getVisibility() == AssignmentVisibility.PUBLIC) {
            tagService.requireCompletePublicTags(assignment);
        }

        Assignment saved = assignmentRepository.save(assignment);
        return assignmentMapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional
    public List<AssignmentResponse> createBatchAssignments(List<CreateAssignmentRequest> requests, long teacherId) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<AssignmentResponse> responses = new ArrayList<>();
        for (CreateAssignmentRequest request : requests) {
            responses.add(createAssignment(request, teacherId));
        }
        return responses;
    }

    @Override
    @Transactional
    public void publishAssignment(long assignmentId, PublishAssignmentRequest request, long teacherId) {
        // 1. Tìm bài tập
        Assignment originalAssignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        validateTeacherOwnership(originalAssignment, teacherId, "Bạn không có quyền publish bài tập này");

        // 3. Kiểm tra trạng thái – cho phép publish khi đang là DRAFT hoặc ARCHIVED
        if (originalAssignment.getStatus() == AssignmentStatus.DELETED) {
            throw new BadRequestException("Bài tập đã bị xóa không thể giao bài");
        }

        // 3.1 Validate đầy đủ thông tin trước khi publish
        if (originalAssignment.getTitle() == null || originalAssignment.getTitle().trim().isEmpty() ||
                originalAssignment.getContent() == null || originalAssignment.getContent().trim().isEmpty()) {
            throw new BadRequestException("Vui lòng điền đầy đủ Tiêu đề và Nội dung trước khi Giao bài");
        }

        List<Assignment> clones = new ArrayList<>();

        // 4. Lặp qua các lớp đích và clone bài tập
        for (PublishAssignmentRequest.TargetClass target : request.getTargets()) {
            String classCode = target.getClassCode();
            Classroom classroom = classroomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy lớp học với mã: " + classCode));

            if (classroom.getTeacher().getId() != teacherId) {
                throw new AccessDeniedException(
                        "Bạn không có quyền giao bài tập cho lớp: " + classCode);
            }

            Assignment clone = cloneAssignmentForClassroom(originalAssignment, classroom, target.getDeadline() != null ? target.getDeadline().minusHours(7) : null);
            clones.add(clone);
        }

        // 5. Lưu tất cả bản clone
        assignmentRepository.saveAll(clones);

        // Gửi email cho từng học sinh trong lớp học
        for (Assignment clone : clones) {
            sendAssignmentNotificationToClassroom(clone, clone.getClassroom());
        }

        // 6. Cập nhật trạng thái bản nháp thành ARCHIVED nếu như đang là DRAFT
        if (originalAssignment.getStatus() == AssignmentStatus.DRAFT) {
            originalAssignment.setStatus(AssignmentStatus.ARCHIVED);
        }
        assignmentRepository.save(originalAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsByClassCode(String classCode, long userId, String keyword,
            AssignmentStatus status, String studentStatus, Pageable pageable) {
        // 1. Tìm lớp học
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với mã: " + classCode));

        // 2. Kiểm tra quyền truy cập (giáo viên hoặc học sinh của lớp)
        boolean isTeacher = classroom.getTeacher().getId() == userId;
        boolean isStudent = classroom.getStudents().stream().anyMatch(student -> student.getId() == userId);

        if (!isTeacher && !isStudent) {
            throw new AccessDeniedException("Bạn không có quyền xem bài tập của lớp này");
        }

        Specification<Assignment> spec = (root, query, cb) -> {
            Join<Assignment, Classroom> classroomJoin = root.join("classroom",
                    JoinType.LEFT);
            // Lấy các bài tập của lớp này
            Predicate isClassCode = cb.equal(classroomJoin.get("classCode"), classCode);

            if (isTeacher) {
                // Giáo viên thấy bài tập của lớp HOẶC các bản nháp của chính họ
                Predicate isDraftAndMyTeacher = cb.and(
                        cb.equal(root.get("status"), AssignmentStatus.DRAFT),
                        cb.equal(root.get("teacher").get("id"), userId));
                return cb.or(isClassCode, isDraftAndMyTeacher);
            } else {
                // Học sinh chỉ thấy bài tập của lớp đó
                return isClassCode;
            }
        };
        
        spec = spec.and(AssignmentSpecification.isNotInSheet());

        // Lọc theo keyword (tiêu đề)
        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasTitleContaining(keyword));
        }

        // Lọc theo status
        if (status != null) {
            if (isStudent && status != AssignmentStatus.PUBLISHED) {
                return Page.empty(pageable);
            }
            spec = spec.and(AssignmentSpecification.hasStatus(status));
        } else {
            if (isStudent) {
                spec = spec.and(AssignmentSpecification.hasStatus(AssignmentStatus.PUBLISHED));
            }
        }

        if (isStudent && studentStatus != null && !studentStatus.isBlank()) {
            spec = spec.and(AssignmentSpecification.hasStudentStatus(userId, studentStatus));
        }

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(
                    Sort.Order.asc("status"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("createdAt"));
        }
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), sort);

        Page<Assignment> assignments = assignmentRepository.findAll(spec, sortedPageable);
        return assignments.map(assignment -> {
            AssignmentResponse response = assignmentMapper.toAssignmentResponseWithoutContent(assignment);
            if (isStudent) {
                submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                        .ifPresent(sub -> {
                            response.setSubmissionStatus(sub.getStatus().name());
                            response.setSubmissionCreatedAt(sub.getCreatedAt());
                            response.setSubmissionUpdatedAt(sub.getUpdatedAt());
                            response.setSubmissionScore(sub.getScore());
                        });
            }
            return response;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsForCurrentUser(long userId, String role, String keyword,
            String classCode, AssignmentStatus status, Long gradeTagId, Long subjectTagId, Long difficultyTagId, List<String> tagNames, String studentStatus, Pageable pageable) {
        Specification<Assignment> spec = (root, query, cb) -> cb.conjunction();
        
        // Loại bỏ những bài tập đã nằm trong phiếu bài tập
        spec = spec.and(AssignmentSpecification.isNotInSheet());

        // 1. Phân quyền truy cập cơ bản theo Role
        if (Role.TEACHER.name().equals(role)) {
            spec = spec.and(AssignmentSpecification.isTeacher(userId));
        } else if (Role.STUDENT.name().equals(role)) {
            // Học sinh chỉ xem được bài tập PUBLISHED
            if (status != null && status != AssignmentStatus.PUBLISHED) {
                // Trả về rỗng nếu cố tình lọc các trạng thái không được phép
                return Page.empty(pageable);
            }
            spec = spec.and(AssignmentSpecification.isStudent(userId))
                    .and(AssignmentSpecification.hasStatus(AssignmentStatus.PUBLISHED));
            if (studentStatus != null && !studentStatus.isBlank()) {
                spec = spec.and(AssignmentSpecification.hasStudentStatus(userId, studentStatus));
            }
        } else {
            throw new AccessDeniedException("Role không hợp lệ");
        }

        // 2. Lọc theo keyword (tiêu đề)
        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasTitleContaining(keyword));
        }

        // 3. Lọc theo classCode
        if (classCode != null && !classCode.trim().isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasClassCode(classCode));
        } else if (Role.TEACHER.name().equals(role)) {
            // Trong Kho bài tập (không filter theo classCode), chỉ lấy các bản gốc (không thuộc lớp nào)
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("classroom")));
        }

        // 4. Lọc theo status (nếu là TEACHER thì có thể filter tùy ý, STUDENT thì
        // status luôn là PUBLISHED đã set ở trên)
        if (status != null && Role.TEACHER.name().equals(role)) {
            spec = spec.and(AssignmentSpecification.hasStatus(status));
        }

        tagService.validateTagFilters(gradeTagId, subjectTagId, difficultyTagId);

        if (gradeTagId != null) spec = spec.and(AssignmentSpecification.hasTag(gradeTagId));
        if (subjectTagId != null) spec = spec.and(AssignmentSpecification.hasTag(subjectTagId));
        if (difficultyTagId != null) spec = spec.and(AssignmentSpecification.hasTag(difficultyTagId));
        if (tagNames != null && !tagNames.isEmpty()) {
            spec = spec.and(AssignmentSpecification.hasTagNames(tagNames));
        }

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(
                    Sort.Order.asc("status"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("createdAt"));
        }
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), sort);

        Page<Assignment> assignments = assignmentRepository.findAll(spec, sortedPageable);
        if (assignments.isEmpty()) {
            return Page.empty(sortedPageable);
        }

        // Batch pre-fetch clones for TEACHER to eliminate N+1 queries
        java.util.Map<Long, List<String>> cloneClassCodesMap = new java.util.HashMap<>();
        if (Role.TEACHER.name().equals(role)) {
            List<Long> parentIds = assignments.getContent().stream().map(Assignment::getId).toList();
            List<Assignment> clones = assignmentRepository.findByParentIdIn(parentIds);
            for (Assignment clone : clones) {
                if (clone.getParentId() != null && clone.getClassroom() != null) {
                    cloneClassCodesMap.computeIfAbsent(clone.getParentId(), k -> new java.util.ArrayList<>())
                            .add(clone.getClassroom().getClassCode());
                }
            }
        }

        // Batch pre-fetch submissions for STUDENT to eliminate N+1 queries
        java.util.Map<Long, com.codegym.mathclass.submission.entity.Submission> submissionMap = new java.util.HashMap<>();
        if (Role.STUDENT.name().equals(role)) {
            List<Long> assignmentIds = assignments.getContent().stream().map(Assignment::getId).toList();
            List<com.codegym.mathclass.submission.entity.Submission> submissions = submissionRepository.findAllByAssignmentIdInAndStudentId(assignmentIds, userId);
            for (com.codegym.mathclass.submission.entity.Submission sub : submissions) {
                submissionMap.putIfAbsent(sub.getAssignment().getId(), sub);
            }
        }

        return assignments.map(assignment -> {
            AssignmentResponse response = assignmentMapper.toAssignmentResponseWithoutContent(assignment);
            if (Role.TEACHER.name().equals(role)) {
                List<String> rawCodes = cloneClassCodesMap.getOrDefault(assignment.getId(), java.util.Collections.emptyList());
                List<String> publishedCodes = new java.util.ArrayList<>(rawCodes.stream().distinct().toList());
                if (assignment.getClassroom() != null && !publishedCodes.contains(assignment.getClassroom().getClassCode())) {
                    publishedCodes.add(assignment.getClassroom().getClassCode());
                }
                response.setPublishedClassCodes(publishedCodes);
            }
            if (Role.STUDENT.name().equals(role)) {
                com.codegym.mathclass.submission.entity.Submission sub = submissionMap.get(assignment.getId());
                if (sub != null) {
                    response.setSubmissionStatus(sub.getStatus().name());
                    response.setSubmissionCreatedAt(sub.getCreatedAt());
                    response.setSubmissionUpdatedAt(sub.getUpdatedAt());
                    response.setSubmissionScore(sub.getScore());
                }
            }
            return response;
        });
    }

    @Override
    @Transactional
    public void deleteAssignment(long assignmentId, long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài tập này");
        }

        // Kiểm tra xem đã có submission hay chưa
        if (submissionRepository.existsByAssignmentId(assignmentId)) {
            throw new BadRequestException("Đã có học sinh nộp bài, không thể xóa bài tập này");
        }


        // 3. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            // Thu thập các URL ảnh cần xóa nếu không bị bài tập khác dùng chung
            List<String> imageUrlsToDelete = new ArrayList<>();
            if (assignment.getImages() != null) {
                for (AssignmentImage img : assignment.getImages()) {
                    if (img.getImageUrl() != null && !assignmentImageRepository.existsByImageUrlAndAssignmentIdNot(img.getImageUrl(), assignmentId)) {
                        imageUrlsToDelete.add(img.getImageUrl());
                    }
                }
            }

            // Bản nháp -> xóa cứng
            assignmentRepository.delete(assignment);

            // Dọn dẹp ảnh mồ côi trên dịch vụ lưu trữ
            for (String url : imageUrlsToDelete) {
                try {
                    storageService.delete(url);
                } catch (Exception e) {
                    // Bắt lỗi an toàn, không gián đoạn luồng
                }
            }
        } else {
            // Không phải nháp -> xóa mềm
            // Nếu là bài gốc (ARCHIVED), các bản clone không bị xóa/ẩn mà đổi parentId =
            // null
            if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
                List<Assignment> clones = assignmentRepository.findByParentId(assignment.getId());
                for (Assignment clone : clones) {
                    clone.setParentId(null);
                }
                assignmentRepository.saveAll(clones);
            }

            assignment.setStatus(AssignmentStatus.DELETED);
            assignmentRepository.save(assignment);
        }
    }

    @Override
    @Transactional
    public AssignmentResponse updateAssignment(long assignmentId, UpdateAssignmentRequest request, long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        validateTeacherOwnership(assignment, teacherId, "Bạn không có quyền sửa bài tập này");

        // 3. Từ chối nếu đã bị xóa
        if (assignment.getStatus() == AssignmentStatus.DELETED) {
            throw new BadRequestException("Không thể sửa bài tập đã bị xóa");
        }

        // 4. Validate LaTeX trong nội dung mới
        if (request.getContent() != null && !LaTeXSanitizer.isSafe(request.getContent())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(request.getContent());
            throw new IllegalArgumentException(
                    "Nội dung chứa lệnh LaTeX không được phép: " + dangerous);
        }

        // 4.1 Validate bắt buộc nếu không phải DRAFT
        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            if (request.getTitle() == null || request.getTitle().trim().isEmpty() ||
                    request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new BadRequestException(
                        "Tiêu đề và Nội dung không được để trống khi bài tập đã được Giao");
            }
        }

        // 5. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            updateDraftAssignment(assignment, request);
            if (request.getTagNames() != null) {
                tagService.replaceTagsByName(assignment, request.getTagNames());
            } else {
                tagService.replaceTags(assignment, request.getTagIds());
            }
            validatePublicTags(assignment);
            assignmentRepository.save(assignment);
        } else if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            updateArchivedAssignment(assignment, request);
            if (request.getTagNames() != null) {
                tagService.replaceTagsByName(assignment, request.getTagNames());
            } else {
                tagService.replaceTags(assignment, request.getTagIds());
            }
            validatePublicTags(assignment);
            assignmentRepository.save(assignment);
        } else if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            updatePublishedAssignment(assignment, request);
            if (request.getTagNames() != null) {
                tagService.replaceTagsByName(assignment, request.getTagNames());
            } else {
                tagService.replaceTags(assignment, request.getTagIds());
            }
            validatePublicTags(assignment);
            assignmentRepository.save(assignment);
        }

        return assignmentMapper.toAssignmentResponse(assignment);
    }

    @Override
    public AssignmentImageResponse uploadImageForAssignment(MultipartFile file) throws IOException {
        String publicUrl = storageService.upload(file, StoragePolicy.ASSIGNMENT_IMAGE);
        String imageCode = "[IMAGE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "]";
        return new AssignmentImageResponse(imageCode, publicUrl);
    }

    @Override
    public Map<String, Object> extractTextFromFile(MultipartFile file) throws Exception {
        String filename = file != null ? file.getOriginalFilename() : null;
        DocumentParserStrategy parser = documentParserFactory.getParser(filename);
        DocumentParseResult result = parser.parse(file);
        return Map.of("content", result.content(), "images", result.images());
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentById(long assignmentId, long userId, String role) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (Role.TEACHER.name().equals(role)) {
            if (assignment.getTeacher().getId() != userId) {
                throw new AccessDeniedException("Bạn không có quyền xem bài tập này");
            }
        } else if (Role.STUDENT.name().equals(role)) {
            if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
                throw new AccessDeniedException("Bạn không thể xem bài tập này");
            }
            if (assignment.getClassroom() == null) {
                throw new BadRequestException("Bài tập chưa được giao cho lớp nào");
            }
            boolean isStudentInClass = assignment.getClassroom().getStudents().stream()
                    .anyMatch(student -> student.getId() == userId);
            if (!isStudentInClass) {
                throw new AccessDeniedException("Bạn không có quyền xem bài tập này");
            }
        } else {
            throw new AccessDeniedException("Vai trò không hợp lệ");
        }

        AssignmentResponse response = assignmentMapper.toAssignmentResponse(assignment);

        if (assignment.getAssignmentSheet() != null) {
            List<SheetSiblingResponse> siblings = assignment.getAssignmentSheet().getItems().stream()
                    .map(item -> {
                        SheetSiblingResponse dto = new SheetSiblingResponse(item.getId(), item.getTitle());
                        if (Role.STUDENT.name().equals(role)) {
                            submissionRepository.findFirstByAssignmentIdAndStudentId(item.getId(), userId)
                                    .ifPresent(sub -> dto.setSubmissionStatus(sub.getStatus().name()));
                        }
                        return dto;
                    })
                    .collect(java.util.stream.Collectors.toList());
            response.setSheetSiblings(siblings);
        }

        if (Role.STUDENT.name().equals(role)) {
            submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                    .ifPresent(sub -> {
                        response.setSubmissionStatus(sub.getStatus().name());
                        response.setSubmissionCreatedAt(sub.getCreatedAt());
                        response.setSubmissionUpdatedAt(sub.getUpdatedAt());
                        response.setSubmissionScore(sub.getScore());
                    });
        }

        return response;
    }

    private void validateTeacherOwnership(Assignment assignment, long teacherId, String errorMessage) {
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException(errorMessage);
        }
    }

    private void updateDrawings(Assignment assignment, List<AssignmentDrawingRequest> drawingReqs) {
        if (drawingReqs != null) {
            assignment.getDrawings().clear();
            for (var drawingReq : drawingReqs) {
                AssignmentDrawing drawing = new AssignmentDrawing();
                drawing.setShapeCode(drawingReq.getShapeCode());
                drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                drawing.setAssignment(assignment);
                assignment.getDrawings().add(drawing);
            }
        }
    }

    private void updateImages(Assignment assignment, List<AssignmentImageRequest> imageReqs) {
        if (imageReqs != null) {
            assignment.getImages().clear();
            for (var imageReq : imageReqs) {
                AssignmentImage img = new AssignmentImage();
                img.setImageCode(imageReq.getImageCode());
                img.setImageUrl(imageReq.getImageUrl());
                img.setAssignment(assignment);
                assignment.getImages().add(img);
            }
        }
    }

    private void updateDraftAssignment(Assignment assignment, UpdateAssignmentRequest request) {
        assignment.setTitle(request.getTitle() != null ? request.getTitle() : "");
        assignment.setDescription(request.getDescription() != null ? request.getDescription() : "");
        assignment.setContent(request.getContent() != null ? request.getContent() : "");
        if (request.getVisibility() != null) {
            assignment.setVisibility(request.getVisibility());
        }
        if (request.getAllowResubmit() != null) {
            assignment.setAllowResubmit(request.getAllowResubmit());
        }

        updateDrawings(assignment, request.getDrawings());
        updateImages(assignment, request.getImages());
    }

    private void updateArchivedAssignment(Assignment assignment, UpdateAssignmentRequest request) {
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setContent(request.getContent());
        if (request.getVisibility() != null) {
            assignment.setVisibility(request.getVisibility());
        }
        if (request.getAllowResubmit() != null) {
            assignment.setAllowResubmit(request.getAllowResubmit());
        }

        updateDrawings(assignment, request.getDrawings());
        updateImages(assignment, request.getImages());

        List<Assignment> publishedClones = assignmentRepository.findByParentId(assignment.getId());
        for (Assignment clone : publishedClones) {
            if (submissionRepository.existsByAssignmentId(clone.getId())) {
                continue;
            }
            clone.setTitle(request.getTitle());
            clone.setDescription(request.getDescription());
            clone.setContent(request.getContent());
            if (request.getAllowResubmit() != null) {
                clone.setAllowResubmit(request.getAllowResubmit());
            }

            updateDrawings(clone, request.getDrawings());
            updateImages(clone, request.getImages());
        }
        assignmentRepository.saveAll(publishedClones);
    }

    private void updatePublishedAssignment(Assignment assignment, UpdateAssignmentRequest request) {
        boolean hasSubmissions = submissionRepository.existsByAssignmentId(assignment.getId());

        if (request.getVisibility() != null) {
            assignment.setVisibility(request.getVisibility());
        }
        if (request.getAllowResubmit() != null) {
            assignment.setAllowResubmit(request.getAllowResubmit());
        }

        if (hasSubmissions) {
            if (!assignment.getTitle().equals(request.getTitle()) ||
                    !assignment.getDescription().equals(request.getDescription()) ||
                    !assignment.getContent().equals(request.getContent())) {
                throw new BadRequestException("Bài tập đã có học sinh nộp bài, bạn chỉ có thể thay đổi hạn nộp");
            }
            if (request.getDeadline() != null) {
                assignment.setDeadline(request.getDeadline().minusHours(7));
            }
        } else {
            assignment.setTitle(request.getTitle());
            assignment.setDescription(request.getDescription());
            assignment.setContent(request.getContent());
            if (request.getDeadline() != null) {
                assignment.setDeadline(request.getDeadline().minusHours(7));
            }
            updateDrawings(assignment, request.getDrawings());
            updateImages(assignment, request.getImages());
        }
    }

    private Assignment cloneAssignmentForClassroom(Assignment original, Classroom classroom,
            java.time.LocalDateTime deadline) {
        Assignment clone = new Assignment();
        clone.setTitle(original.getTitle());
        clone.setDescription(original.getDescription());
        clone.setContent(original.getContent());
        clone.setTeacher(original.getTeacher());
        clone.setParentId(original.getId());
        clone.setClassroom(classroom);
        clone.setDeadline(deadline);
        clone.setStatus(AssignmentStatus.PUBLISHED);
        clone.setAllowResubmit(original.isAllowResubmit());
        tagService.copyTags(original, clone);

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
        return clone;
    }

    private void validatePublicTags(Assignment assignment) {
        if (assignment.getVisibility() == AssignmentVisibility.PUBLIC) {
            tagService.requireCompletePublicTags(assignment);
        }
    }

    private void sendAssignmentNotificationToClassroom(Assignment clone, Classroom classroom) {
        if (classroom != null && classroom.getStudents() != null) {
            for (User student : classroom.getStudents()) {
                Context context = new Context();
                context.setVariable("studentName", student.getFullName());
                context.setVariable("assignmentName", clone.getTitle());
                context.setVariable("link", frontendUrl + "/assignments/" + clone.getId());

                emailService.sendHtmlMailAsync(
                        student.getEmail(),
                        "Bài tập mới: " + clone.getTitle(),
                        "assignment-notification",
                        context);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getPublicAssignments(String keyword, Pageable pageable) {
        Specification<Assignment> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("visibility"), AssignmentVisibility.PUBLIC),
                cb.isNull(root.get("classroom")),
                cb.notEqual(root.get("status"), AssignmentStatus.DELETED)
        );

        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
        }

        Page<Assignment> assignments = assignmentRepository.findAll(spec, pageable);
        return assignments.map(assignmentMapper::toAssignmentResponseWithoutContent);
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

        User originalAuthor = original.getOriginalAuthor() != null ? original.getOriginalAuthor() : original.getTeacher();

        Assignment clone = Assignment.builder()
                .title(original.getTitle())
                .description(original.getDescription())
                .content(original.getContent())
                .status(AssignmentStatus.DRAFT)
                .visibility(AssignmentVisibility.PRIVATE)
                .teacher(teacher)
                .originalAuthor(originalAuthor)
                .classroom(null)
                .build();
        tagService.copyTags(original, clone);

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

        Assignment saved = assignmentRepository.save(clone);
        return assignmentMapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional
    public AssignmentResponse updateAssignmentVisibility(long assignmentId, UpdateVisibilityRequest request, long teacherId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi visibility của bài tập này");
        }

        if (request.getVisibility() == AssignmentVisibility.PUBLIC) {
            tagService.requireCompletePublicTags(assignment);
        }
        assignment.setVisibility(request.getVisibility());
        Assignment saved = assignmentRepository.save(assignment);
        return assignmentMapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional
    public AssignmentResponse toggleAllowResubmit(long assignmentId, boolean allowResubmit, long teacherId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi thiết lập của bài tập này");
        }

        assignment.setAllowResubmit(allowResubmit);

        // Nếu là bài ARCHIVED (gốc), đồng bộ sang tất cả các bản PUBLISHED con
        if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            List<Assignment> clones = assignmentRepository.findByParentId(assignment.getId());
            for (Assignment clone : clones) {
                clone.setAllowResubmit(allowResubmit);
            }
            assignmentRepository.saveAll(clones);
        }

        Assignment saved = assignmentRepository.save(assignment);
        return assignmentMapper.toAssignmentResponse(saved);
    }
}
