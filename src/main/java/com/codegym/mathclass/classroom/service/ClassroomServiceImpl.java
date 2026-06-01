package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request, Long currentUserId) {
        User teacher = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (teacher.getRole() != Role.TEACHER) {
            throw new RuntimeException("Chỉ giáo viên mới có quyền tạo lớp học");
        }

        String generatedClassCode;
        do {
            generatedClassCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (classroomRepository.existsByClassCode(generatedClassCode));

        Classroom classroom = new Classroom();
        classroom.setClassName(request.getName());
        classroom.setClassCode(generatedClassCode);
        classroom.setMaxStudents(request.getMaxStudents());
        classroom.setDescription(request.getDescription());
        classroom.setTeacher(teacher);
        // Danh sách học sinh được khởi tạo rỗng mặc định trong entity

        Classroom savedClassroom = classroomRepository.save(classroom);

        return ClassroomResponse.fromEntity(savedClassroom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomResponse> getClassroomsListById(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        List<Classroom> classrooms;

        if (user.getRole() == Role.TEACHER) {
            classrooms = classroomRepository.findByTeacherId(currentUserId);
        } else if (user.getRole() == Role.STUDENT) {
            classrooms = classroomRepository.findByStudentsId(currentUserId);
        } else {
            throw new RuntimeException("Vai trò người dùng không hợp lệ để xem danh sách lớp");
        }

        return classrooms.stream()
                .map(ClassroomResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addStudentToClass(String classCode, String studentEmail, Long teacherId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lớp học"));

        if (classroom.getTeacher().getId() != teacherId) {
            throw new RuntimeException("Bạn không phải là giáo viên phụ trách lớp học này");
        }

        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy học sinh với email đã cung cấp"));

        if (student.getRole() != Role.STUDENT) {
            throw new RuntimeException("Người dùng được thêm phải là học sinh");
        }

        if (classroom.getStudents().stream().anyMatch(s -> s.getEmail().equalsIgnoreCase(studentEmail))) {
            throw new RuntimeException("Học sinh này đã tham gia lớp học");
        }

        if (classroom.getMaxStudents() != null && classroom.getStudents().size() >= classroom.getMaxStudents()) {
            throw new RuntimeException("Lớp học đã đạt số lượng tối đa");
        }

        classroom.getStudents().add(student);
        classroomRepository.save(classroom);

        String subject = "Bạn đã được thêm vào lớp học " + classroom.getClassName();
        String content = String.format(
                "Xin chào %s,\n\nBạn đã được giáo viên %s thêm vào lớp học: %s (%s) trên hệ thống MathClass.\n\nChúc bạn học tập tốt!",
                student.getFullName(),
                classroom.getTeacher().getFullName(),
                classroom.getClassName(),
                classroom.getClassCode());
        emailService.sendMail(studentEmail, subject, content);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getStudentsByClassCode(String classCode, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lớp học"));

        boolean isTeacher = classroom.getTeacher().getId().equals(currentUserId);
        boolean isStudent = classroom.getStudents().stream().anyMatch(s -> s.getId().equals(currentUserId));

        if (!isTeacher && !isStudent) {
            throw new RuntimeException("Bạn không có quyền xem danh sách học sinh lớp học này");
        }

        return classroom.getStudents().stream()
                .map(StudentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomResponse getClassroomByClassCode(String classCode, Long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lớp học"));

        boolean isTeacher = classroom.getTeacher().getId().equals(currentUserId);
        boolean isStudent = classroom.getStudents().stream().anyMatch(s -> s.getId().equals(currentUserId));

        if (!isTeacher && !isStudent) {
            throw new RuntimeException("Bạn không có quyền xem thông tin lớp học này");
        }

        return ClassroomResponse.fromEntity(classroom);
    }
}
