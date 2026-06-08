package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import com.codegym.mathclass.assignment.repository.AssignmentSpecification;
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

@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;

    @Override
    @Transactional
    public AssignmentResponse createAssignment(CreateAssignmentRequest request, Long teacherId) {
        // 1. Tìm giáo viên
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // 2. Kiểm tra vai trò
        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Chỉ giáo viên mới có quyền tạo bài tập");
        }

        // 3. Validate LaTeX trong mô tả
        if (!LaTeXSanitizer.isSafe(request.getDescription())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(request.getDescription());
            throw new IllegalArgumentException(
                    "Mô tả chứa lệnh LaTeX không được phép: " + dangerous);
        }

        // 4. Tạo bài tập với trạng thái DRAFT, chưa gán lớp và chưa có deadline
        Assignment assignment = new Assignment();
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setStatus(AssignmentStatus.DRAFT);
        assignment.setTeacher(teacher);
        assignment.setClassroom(null);
        // deadline = null cho đến khi giáo viên publish

        Assignment saved = assignmentRepository.save(assignment);
        return AssignmentResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void publishAssignment(Long assignmentId, PublishAssignmentRequest request, Long teacherId) {
        // 1. Tìm bài tập
        Assignment originalAssignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (!originalAssignment.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền publish bài tập này");
        }

        // 3. Kiểm tra trạng thái – chỉ publish được khi đang là DRAFT
        if (originalAssignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BadRequestException("Bài tập đã được publish hoặc archive trước đó");
        }

        List<Assignment> clones = new ArrayList<>();

        // 4. Lặp qua các lớp đích và clone bài tập
        for (PublishAssignmentRequest.TargetClass target : request.getTargets()) {
            String classCode = target.getClassCode();
            Classroom classroom = classroomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy lớp học với mã: " + classCode));

            if (!classroom.getTeacher().getId().equals(teacherId)) {
                throw new AccessDeniedException(
                        "Bạn không có quyền giao bài tập cho lớp: " + classCode);
            }

            Assignment clone = new Assignment();
            clone.setTitle(originalAssignment.getTitle());
            clone.setDescription(originalAssignment.getDescription());
            clone.setTeacher(originalAssignment.getTeacher());
            clone.setParentId(originalAssignment.getId());
            clone.setClassroom(classroom);
            clone.setDeadline(target.getDeadline());
            clone.setStatus(AssignmentStatus.PUBLISHED);

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
    public Page<AssignmentResponse> getAssignmentsByClassCode(String classCode, Long userId, String keyword,
            AssignmentStatus status, Pageable pageable) {
        // 1. Tìm lớp học
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với mã: " + classCode));

        // 2. Kiểm tra quyền truy cập (giáo viên hoặc học sinh của lớp)
        boolean isTeacher = classroom.getTeacher().getId().equals(userId);
        boolean isStudent = classroom.getStudents().stream().anyMatch(student -> student.getId().equals(userId));

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

        Page<Assignment> assignments = assignmentRepository.findAll(spec, pageable);
        return assignments.map(AssignmentResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> getAssignmentsForCurrentUser(Long userId, String role, String keyword,
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

        Page<Assignment> assignments = assignmentRepository.findAll(spec, pageable);
        return assignments.map(AssignmentResponse::fromEntity);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long assignmentId, Long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (!assignment.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài tập này");
        }

        // TODO: Kiểm tra xem đã có submission hay chưa (hiện tại chưa có chức năng nộp bài)

        // 3. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            // Bản nháp -> xóa cứng
            assignmentRepository.delete(assignment);
        } else {
            // Không phải nháp -> xóa mềm
            // Nếu là bài gốc (ARCHIVED), các bản clone không bị xóa/ẩn mà đổi parentId = null
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
    public AssignmentResponse updateAssignment(Long assignmentId, UpdateAssignmentRequest request, Long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (!assignment.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền sửa bài tập này");
        }

        // 3. Từ chối nếu đã bị xóa
        if (assignment.getStatus() == AssignmentStatus.DELETED) {
            throw new BadRequestException("Không thể sửa bài tập đã bị xóa");
        }

        // 4. Validate LaTeX trong mô tả mới
        if (!LaTeXSanitizer.isSafe(request.getDescription())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(request.getDescription());
            throw new IllegalArgumentException(
                    "Mô tả chứa lệnh LaTeX không được phép: " + dangerous);
        }

        // 5. Xử lý theo trạng thái
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            // DRAFT: sửa tự do, không có deadline
            assignment.setTitle(request.getTitle());
            assignment.setDescription(request.getDescription());
            assignmentRepository.save(assignment);

        } else if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            // ARCHIVED (bản gốc): cập nhật bản gốc
            assignment.setTitle(request.getTitle());
            assignment.setDescription(request.getDescription());
            assignmentRepository.save(assignment);

            // Đồng bộ sang tất cả bản PUBLISHED con
            List<Assignment> publishedClones = assignmentRepository.findByParentId(assignment.getId());
            for (Assignment clone : publishedClones) {
                // TODO: Bỏ qua bản clone đã có submission khi Submission module được xây dựng.
                //       Ví dụ: if (submissionRepository.existsByAssignmentId(clone.getId())) continue;
                clone.setTitle(request.getTitle());
                clone.setDescription(request.getDescription());
            }
            assignmentRepository.saveAll(publishedClones);

        } else if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            // PUBLISHED: sửa title + description + deadline (nếu chưa có submission)
            // TODO: Kiểm tra submission khi Submission module được xây dựng.
            //       Ví dụ: if (submissionRepository.existsByAssignmentId(assignmentId))
            //                  throw new BadRequestException("Đã có học sinh nộp bài, không thể sửa");
            assignment.setTitle(request.getTitle());
            assignment.setDescription(request.getDescription());
            if (request.getDeadline() != null) {
                assignment.setDeadline(request.getDeadline());
            }
            assignmentRepository.save(assignment);
        }

        return AssignmentResponse.fromEntity(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentById(Long assignmentId, Long userId, String role) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (Role.TEACHER.name().equals(role)) {
            if (!assignment.getTeacher().getId().equals(userId)) {
                throw new AccessDeniedException("Bạn không có quyền xem bài tập này");
            }
        } else if (Role.STUDENT.name().equals(role)) {
            if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
                throw new AccessDeniedException("Bạn không thể xem bài tập này");
            }
            if (assignment.getClassroom() != null) {
                boolean isStudentInClass = assignment.getClassroom().getStudents().stream()
                        .anyMatch(student -> student.getId().equals(userId));
                if (!isStudentInClass) {
                    throw new AccessDeniedException("Bạn không thuộc lớp của bài tập này");
                }
            } else {
                throw new AccessDeniedException("Bài tập chưa được giao cho lớp nào");
            }
        } else {
            throw new AccessDeniedException("Role không hợp lệ");
        }

        return AssignmentResponse.fromEntity(assignment);
    }
}
