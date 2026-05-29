package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
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

    @Override
    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request, String currentUserEmail) {
        User teacher = userRepository.findByEmail(currentUserEmail)
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
    public List<ClassroomResponse> getClassroomsByTeacher(String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        if (teacher.getRole() != Role.TEACHER) {
            throw new RuntimeException("Chỉ giáo viên mới có quyền xem danh sách lớp học");
        }
        return classroomRepository.findByTeacherEmail(teacherEmail).stream()
                .map(ClassroomResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
