package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.CreateStudentRemarkRequest;
import com.codegym.mathclass.classroom.dto.StudentRemarkResponse;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.entity.StudentRemark;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.classroom.repository.StudentRemarkRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentRemarkServiceImpl implements StudentRemarkService {

    private final StudentRemarkRepository studentRemarkRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StudentRemarkResponse> getStudentRemarks(String classCode, Long studentId, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        boolean isTeacher = Objects.equals(classroom.getTeacher().getId(), currentUserId);
        boolean isStudentHimself = Objects.equals(studentId, currentUserId);

        if (!isTeacher && !isStudentHimself) {
            throw new AccessDeniedException("Bạn không có quyền xem nhận xét của học sinh này");
        }

        List<StudentRemark> remarks = studentRemarkRepository.findByClassCodeAndStudentIdOrderByCreatedAtDesc(classCode, studentId);
        return remarks.stream()
                .map(StudentRemarkResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StudentRemarkResponse createStudentRemark(String classCode, Long studentId, Long currentUserId, CreateStudentRemarkRequest request) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        if (!Objects.equals(classroom.getTeacher().getId(), currentUserId)) {
            throw new AccessDeniedException("Chỉ giáo viên phụ trách lớp mới có quyền nhận xét học sinh");
        }

        User teacher = classroom.getTeacher();
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));

        boolean isMember = classroom.getStudents().stream().anyMatch(s -> Objects.equals(s.getId(), studentId));
        if (!isMember) {
            throw new BadRequestException("Học sinh không thuộc lớp học này");
        }

        boolean hasStrength = request.getStrengths() != null && !request.getStrengths().trim().isEmpty();
        boolean hasWeakness = request.getWeaknesses() != null && !request.getWeaknesses().trim().isEmpty();
        boolean hasGeneral = request.getGeneralAssessment() != null && !request.getGeneralAssessment().trim().isEmpty();

        if (!hasStrength && !hasWeakness && !hasGeneral) {
            throw new BadRequestException("Vui lòng nhập ít nhất điểm mạnh, điểm yếu hoặc nhận xét chung");
        }

        StudentRemark remark = StudentRemark.builder()
                .classroom(classroom)
                .student(student)
                .teacher(teacher)
                .strengths(request.getStrengths() != null ? request.getStrengths().trim() : null)
                .weaknesses(request.getWeaknesses() != null ? request.getWeaknesses().trim() : null)
                .generalAssessment(request.getGeneralAssessment() != null ? request.getGeneralAssessment().trim() : null)
                .build();

        StudentRemark saved = studentRemarkRepository.save(remark);
        return StudentRemarkResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteStudentRemark(String classCode, Long studentId, Long remarkId, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        StudentRemark remark = studentRemarkRepository.findById(remarkId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhận xét"));

        if (!Objects.equals(remark.getClassroom().getId(), classroom.getId())
                || !Objects.equals(remark.getStudent().getId(), studentId)) {
            throw new BadRequestException("Nhận xét không thuộc về học sinh hoặc lớp học này");
        }

        if (!Objects.equals(remark.getTeacher().getId(), currentUserId) && !Objects.equals(classroom.getTeacher().getId(), currentUserId)) {
            throw new AccessDeniedException("Bạn không có quyền xóa nhận xét này");
        }

        studentRemarkRepository.delete(remark);
    }
}
