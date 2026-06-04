package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // 2. Kiểm tra vai trò
        if (teacher.getRole() != Role.TEACHER) {
            throw new RuntimeException("Chỉ giáo viên mới có quyền tạo bài tập");
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
        assignment.setClassrooms(new HashSet<>());
        // deadline = null cho đến khi giáo viên publish

        Assignment saved = assignmentRepository.save(assignment);
        return AssignmentResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public AssignmentResponse publishAssignment(Long assignmentId, PublishAssignmentRequest request, Long teacherId) {
        // 1. Tìm bài tập
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài tập"));

        // 2. Kiểm tra quyền sở hữu
        if (!assignment.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Bạn không có quyền publish bài tập này");
        }

        // 3. Kiểm tra trạng thái – chỉ publish được khi đang là DRAFT
        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new RuntimeException("Bài tập đã được publish trước đó");
        }

        // 4. Tìm và kiểm tra các lớp được chọn – phải thuộc về giáo viên này
        List<String> classCodes = request.getClassCodes();
        Set<Classroom> classrooms = new HashSet<>();

        for (String classCode : classCodes) {
            Classroom classroom = classroomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException(
                            "Không tìm thấy lớp học với mã: " + classCode));

            if (!classroom.getTeacher().getId().equals(teacherId)) {
                throw new RuntimeException(
                        "Bạn không có quyền giao bài tập cho lớp: " + classCode);
            }

            classrooms.add(classroom);
        }

        // 5. Gán lớp, đặt deadline, chuyển trạng thái PUBLISHED, lưu DB
        assignment.setClassrooms(classrooms);
        assignment.setDeadline(request.getDeadline());
        assignment.setStatus(AssignmentStatus.PUBLISHED);

        Assignment saved = assignmentRepository.save(assignment);
        return AssignmentResponse.fromEntity(saved);
    }
}
