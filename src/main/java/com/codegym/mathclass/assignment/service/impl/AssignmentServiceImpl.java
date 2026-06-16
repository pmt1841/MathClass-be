package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import com.codegym.mathclass.assignment.mapper.AssignmentMapper;

@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentMapper assignmentMapper;

    @Override
    @Transactional
    public AssignmentResponse createAssignment(CreateAssignmentRequest request, long teacherId) {
        // 1. Tìm giáo viên
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // 2. Kiểm tra vai trò
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
        Assignment assignment = new Assignment();
        assignment.setTitle(request.getTitle() != null ? request.getTitle() : "");
        assignment.setDescription(request.getDescription() != null ? request.getDescription() : "");
        assignment.setContent(request.getContent() != null ? request.getContent() : "");
        assignment.setStatus(AssignmentStatus.DRAFT);
        assignment.setTeacher(teacher);
        assignment.setClassroom(null);
        // deadline = null cho đến khi giáo viên publish

        if (request.getDrawings() != null && !request.getDrawings().isEmpty()) {
            List<AssignmentDrawing> drawings = new ArrayList<>();
            for (var drawingReq : request.getDrawings()) {
                AssignmentDrawing drawing = new AssignmentDrawing();
                drawing.setShapeCode(drawingReq.getShapeCode());
                drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                drawing.setAssignment(assignment);
                drawings.add(drawing);
            }
            assignment.setDrawings(drawings);
        }

        Assignment saved = assignmentRepository.save(assignment);
        return assignmentMapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional
    public void publishAssignment(long assignmentId, PublishAssignmentRequest request, long teacherId) {
        // 1. Tìm bài tập
        Assignment originalAssignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (originalAssignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền publish bài tập này");
        }

        // 3. Kiểm tra trạng thái – chỉ publish được khi đang là DRAFT
        if (originalAssignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BadRequestException("Bài tập đã được publish hoặc archive trước đó");
        }

        // 3.1 Validate đầy đủ thông tin trước khi publish
        if (originalAssignment.getTitle() == null || originalAssignment.getTitle().trim().isEmpty() ||
                originalAssignment.getDescription() == null || originalAssignment.getDescription().trim().isEmpty() ||
                originalAssignment.getContent() == null || originalAssignment.getContent().trim().isEmpty()) {
            throw new BadRequestException("Vui lòng điền đầy đủ Tiêu đề, Mô tả và Nội dung trước khi Giao bài");
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

            Assignment clone = new Assignment();
            clone.setTitle(originalAssignment.getTitle());
            clone.setDescription(originalAssignment.getDescription());
            clone.setContent(originalAssignment.getContent());
            clone.setTeacher(originalAssignment.getTeacher());
            clone.setParentId(originalAssignment.getId());
            clone.setClassroom(classroom);
            clone.setDeadline(target.getDeadline());
            clone.setStatus(AssignmentStatus.PUBLISHED);

            if (originalAssignment.getDrawings() != null) {
                for (AssignmentDrawing originalDrawing : originalAssignment.getDrawings()) {
                    AssignmentDrawing drawing = new AssignmentDrawing();
                    drawing.setShapeCode(originalDrawing.getShapeCode());
                    drawing.setJsxGraphData(originalDrawing.getJsxGraphData());
                    drawing.setAssignment(clone);
                    clone.getDrawings().add(drawing);
                }
            }

            clones.add(clone);
        }

        // 5. Lưu tất cả bản clone
        assignmentRepository.saveAll(clones);

        // 6. Cập nhật trạng thái bản nháp thành ARCHIVED nếu như đang là DRAFT
        if (originalAssignment.getStatus() == AssignmentStatus.DRAFT) {
            originalAssignment.setStatus(AssignmentStatus.ARCHIVED);
        }
        assignmentRepository.save(originalAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsByClassCode(String classCode, long userId, String keyword,
            AssignmentStatus status, Pageable pageable) {
        // 1. Tìm lớp học
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với mã: " + classCode));

        // 2. Kiểm tra quyền truy cập (giáo viên hoặc học sinh của lớp)
        boolean isTeacher = classroom.getTeacher().getId() == userId;
        boolean isStudent = classroom.getStudents().stream().anyMatch(student -> student.getId() == userId);

        if (!isTeacher && !isStudent) {
            throw new AccessDeniedException("Bạn không có quyền xem bài tập của lớp này");
        }

        Specification<Assignment> spec = Specification.where((root, query, cb) -> {
            jakarta.persistence.criteria.Join<Assignment, Classroom> classroomJoin = root.join("classroom",
                    jakarta.persistence.criteria.JoinType.LEFT);
            // Lấy các bài tập của lớp này
            jakarta.persistence.criteria.Predicate isClassCode = cb.equal(classroomJoin.get("classCode"), classCode);

            if (isTeacher) {
                // Giáo viên thấy bài tập của lớp HOẶC các bản nháp của chính họ
                jakarta.persistence.criteria.Predicate isDraftAndMyTeacher = cb.and(
                        cb.equal(root.get("status"), AssignmentStatus.DRAFT),
                        cb.equal(root.get("teacher").get("id"), userId));
                return cb.or(isClassCode, isDraftAndMyTeacher);
            } else {
                // Học sinh chỉ thấy bài tập của lớp đó
                return isClassCode;
            }
        });

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

        org.springframework.data.domain.Sort customSort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("updatedAt"),
                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                org.springframework.data.domain.Sort.Order.asc("title"));
        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), customSort);

        Page<Assignment> assignments = assignmentRepository.findAll(spec, sortedPageable);
        return assignments.map(assignment -> {
            AssignmentResponse response = assignmentMapper.toAssignmentResponseWithoutContent(assignment);
            if (isStudent) {
                submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                        .ifPresent(sub -> response.setSubmissionStatus(sub.getStatus().name()));
            }
            return response;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsForCurrentUser(long userId, String role, String keyword,
            String classCode, AssignmentStatus status, Pageable pageable) {
        Specification<Assignment> spec = Specification.where((root, query, cb) -> cb.conjunction());

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
        }

        // 4. Lọc theo status (nếu là TEACHER thì có thể filter tùy ý, STUDENT thì
        // status luôn là PUBLISHED đã set ở trên)
        if (status != null && Role.TEACHER.name().equals(role)) {
            spec = spec.and(AssignmentSpecification.hasStatus(status));
        }

        org.springframework.data.domain.Sort customSort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("updatedAt"),
                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                org.springframework.data.domain.Sort.Order.asc("title"));
        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), customSort);

        Page<Assignment> assignments = assignmentRepository.findAll(spec, sortedPageable);
        return assignments.map(assignment -> {
            AssignmentResponse response = assignmentMapper.toAssignmentResponseWithoutContent(assignment);
            if (Role.STUDENT.name().equals(role)) {
                submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), userId)
                        .ifPresent(sub -> response.setSubmissionStatus(sub.getStatus().name()));
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
            // Bản nháp -> xóa cứng
            assignmentRepository.delete(assignment);
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

        // 2. Kiểm tra quyền sở hữu
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền sửa bài tập này");
        }

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
                    request.getDescription() == null || request.getDescription().trim().isEmpty() ||
                    request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new BadRequestException(
                        "Tiêu đề, Mô tả và Nội dung không được để trống khi bài tập đã được Giao");
            }
        }

        // 5. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            // DRAFT: sửa tự do, không có deadline
            assignment.setTitle(request.getTitle() != null ? request.getTitle() : "");
            assignment.setDescription(request.getDescription() != null ? request.getDescription() : "");
            assignment.setContent(request.getContent() != null ? request.getContent() : "");

            if (request.getDrawings() != null) {
                assignment.getDrawings().clear();
                for (var drawingReq : request.getDrawings()) {
                    AssignmentDrawing drawing = new AssignmentDrawing();
                    drawing.setShapeCode(drawingReq.getShapeCode());
                    drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                    drawing.setAssignment(assignment);
                    assignment.getDrawings().add(drawing);
                }
            }

            assignmentRepository.save(assignment);

        } else if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            // ARCHIVED (bản gốc): cập nhật bản gốc
            assignment.setTitle(request.getTitle());
            assignment.setDescription(request.getDescription());
            assignment.setContent(request.getContent());

            if (request.getDrawings() != null) {
                assignment.getDrawings().clear();
                for (var drawingReq : request.getDrawings()) {
                    AssignmentDrawing drawing = new AssignmentDrawing();
                    drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                    drawing.setAssignment(assignment);
                    assignment.getDrawings().add(drawing);
                }
            }
            assignmentRepository.save(assignment);

            // Đồng bộ sang tất cả bản PUBLISHED con
            List<Assignment> publishedClones = assignmentRepository.findByParentId(assignment.getId());
            for (Assignment clone : publishedClones) {
                // Bỏ qua bản clone đã có submission
                if (submissionRepository.existsByAssignmentId(clone.getId())) {
                    continue;
                }
                clone.setTitle(request.getTitle());
                clone.setDescription(request.getDescription());
                clone.setContent(request.getContent());

                if (request.getDrawings() != null) {
                    clone.getDrawings().clear();
                    for (var drawingReq : request.getDrawings()) {
                        AssignmentDrawing drawing = new AssignmentDrawing();
                        drawing.setShapeCode(drawingReq.getShapeCode());
                        drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                        drawing.setAssignment(clone);
                        clone.getDrawings().add(drawing);
                    }
                }
            }
            assignmentRepository.saveAll(publishedClones);

        } else if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            boolean hasSubmissions = submissionRepository.existsByAssignmentId(assignmentId);

            if (hasSubmissions) {
                // Nếu đã có bài nộp, chỉ cho phép cập nhật deadline
                // Ném lỗi nếu cố tình thay đổi title, description hoặc content
                if (!assignment.getTitle().equals(request.getTitle()) ||
                        !assignment.getDescription().equals(request.getDescription()) ||
                        !assignment.getContent().equals(request.getContent())) {
                    throw new BadRequestException(
                            "Bài tập đã có học sinh nộp bài, bạn chỉ có thể thay đổi hạn nộp");
                }

                if (request.getDeadline() != null) {
                    assignment.setDeadline(request.getDeadline());
                }
            } else {
                // Nếu chưa có bài nộp, cho phép sửa tất cả
                assignment.setTitle(request.getTitle());
                assignment.setDescription(request.getDescription());
                assignment.setContent(request.getContent());
                if (request.getDeadline() != null) {
                    assignment.setDeadline(request.getDeadline());
                }

                if (request.getDrawings() != null) {
                    assignment.getDrawings().clear();
                    for (var drawingReq : request.getDrawings()) {
                        AssignmentDrawing drawing = new AssignmentDrawing();
                        drawing.setShapeCode(drawingReq.getShapeCode());
                        drawing.setJsxGraphData(drawingReq.getJsxGraphData());
                        drawing.setAssignment(assignment);
                        assignment.getDrawings().add(drawing);
                    }
                }
            }
            assignmentRepository.save(assignment);
        }

        return assignmentMapper.toAssignmentResponse(assignment);
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
        } else {
            throw new AccessDeniedException("Vai trò không hợp lệ");
        }

        AssignmentResponse response = assignmentMapper.toAssignmentResponse(assignment);

        return response;
    }
}
