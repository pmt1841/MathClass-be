package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.thymeleaf.context.Context;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request, long currentUserId) {
        User teacher = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        Classroom classroom = Classroom.builder()
                .className(request.getName())
                .classCode(generateUniqueClassCode())
                .maxStudents(request.getMaxStudents())
                .description(request.getDescription())
                .teacher(teacher)
                .build();

        Classroom savedClassroom = classroomRepository.save(classroom);
        return ClassroomResponse.fromEntity(savedClassroom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomResponse> getClassroomsListById(long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        List<Classroom> classrooms;

        if (user.getRole() == Role.TEACHER) {
            classrooms = classroomRepository.findByTeacherId(currentUserId);
        } else if (user.getRole() == Role.STUDENT) {
            classrooms = classroomRepository.findByStudentsId(currentUserId);
        } else {
            throw new AccessDeniedException("Vai trò người dùng không hợp lệ để xem danh sách lớp");
        }

        return classrooms.stream()
                .sorted(this::sortClassrooms)
                .map(ClassroomResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addStudentToClass(String classCode, String studentEmail, long teacherId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        validateTeacherPrivilege(classroom, teacherId);

        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh với email đã cung cấp"));

        if (student.getRole() != Role.STUDENT) {
            throw new BadRequestException("Người dùng được thêm phải là học sinh");
        }

        if (classroom.getStudents().stream().anyMatch(s -> s.getEmail().equalsIgnoreCase(studentEmail))) {
            throw new BadRequestException("Học sinh này đã tham gia lớp học");
        }

        if (classroom.getMaxStudents() != null && classroom.getStudents().size() >= classroom.getMaxStudents()) {
            throw new BadRequestException("Lớp học đã đạt số lượng tối đa");
        }

        classroom.getStudents().add(student);
        classroomRepository.save(classroom);

        String subject = "Bạn đã được thêm vào lớp học " + classroom.getClassName();
        String classroomLink = frontendUrl + "/classes/" + classroom.getClassCode();
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("isAdded", true);
        context.setVariable("teacherName", classroom.getTeacher().getFullName());
        context.setVariable("className", classroom.getClassName());
        context.setVariable("classCode", classroom.getClassCode());
        context.setVariable("classroomLink", classroomLink);
        emailService.sendHtmlMailAsync(studentEmail, subject, "classroom-action", context);
        notificationService.saveAndSendNotification(student.getId(), subject, "/classes/" + classroom.getClassCode());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StudentResponse> getStudentsByClassCode(String classCode, long currentUserId, Pageable pageable) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        validateTeacherOrStudentPrivilege(classroom, currentUserId);

        Page<User> studentPage = userRepository.findStudentsByClassCode(classCode, pageable);
        return studentPage.map(StudentResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomResponse getClassroomByClassCode(String classCode, long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        validateTeacherOrStudentPrivilege(classroom, currentUserId);

        return ClassroomResponse.fromEntity(classroom);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(String classCode, long studentId, long teacherId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        validateTeacherPrivilege(classroom, teacherId);

        User studentToRemove = classroom.getStudents().stream()
                .filter(s -> s.getId() == studentId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Học sinh không tồn tại trong lớp học này"));

        classroom.getStudents().remove(studentToRemove);
        classroomRepository.save(classroom);

        String subject = "Bạn đã bị xóa khỏi lớp học " + classroom.getClassName();
        Context context = new Context();
        context.setVariable("studentName", studentToRemove.getFullName());
        context.setVariable("isAdded", false);
        context.setVariable("teacherName", classroom.getTeacher().getFullName());
        context.setVariable("className", classroom.getClassName());
        context.setVariable("classCode", classroom.getClassCode());
        emailService.sendHtmlMailAsync(studentToRemove.getEmail(), subject, "classroom-action", context);
        notificationService.saveAndSendNotification(studentToRemove.getId(), subject, null);
    }

    @Override
    @Transactional
    public ClassroomResponse updateClassroom(String classCode, UpdateClassroomRequest request, long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        validateTeacherPrivilege(classroom, currentUserId, "Bạn không có quyền chỉnh sửa lớp học này");

        int currentStudentCount = classroom.getStudents().size();
        if (request.getMaxStudents() != null && request.getMaxStudents() < currentStudentCount) {
            throw new BadRequestException(
                    "Sĩ số tối đa không được nhỏ hơn sĩ số học sinh hiện tại (" + currentStudentCount + ")");
        }

        classroom.setClassName(request.getClassName());
        classroom.setDescription(request.getDescription());
        classroom.setMaxStudents(request.getMaxStudents());

        Classroom savedClassroom = classroomRepository.save(classroom);
        return ClassroomResponse.fromEntity(savedClassroom);
    }

    @Override
    @Transactional
    public void deleteClassroom(String classCode, long currentUserId) {
        Classroom classroom = classroomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        validateTeacherPrivilege(classroom, currentUserId, "Bạn không có quyền xóa lớp học này");

        if (!classroom.getStudents().isEmpty()) {
            throw new BadRequestException("Không thể xóa lớp học đã có học sinh tham gia");
        }

        classroomRepository.delete(classroom);
    }

    private int sortClassrooms(Classroom c1, Classroom c2) {
        if (c1.getUpdatedAt() != null && c2.getUpdatedAt() != null) {
            int compareResult = c2.getUpdatedAt().compareTo(c1.getUpdatedAt());
            if (compareResult != 0)
                return compareResult;
        } else if (c1.getUpdatedAt() != null)
            return -1;
        else if (c2.getUpdatedAt() != null)
            return 1;

        if (c1.getCreatedAt() != null && c2.getCreatedAt() != null) {
            int compareResult = c2.getCreatedAt().compareTo(c1.getCreatedAt());
            if (compareResult != 0)
                return compareResult;
        } else if (c1.getCreatedAt() != null)
            return -1;
        else if (c2.getCreatedAt() != null)
            return 1;

        if (c1.getClassName() != null && c2.getClassName() != null) {
            return c1.getClassName().compareToIgnoreCase(c2.getClassName());
        }
        return 0;
    }

    private void validateTeacherPrivilege(Classroom classroom, long currentUserId) {
        validateTeacherPrivilege(classroom, currentUserId, "Bạn không phải là giáo viên phụ trách lớp học này");
    }

    private void validateTeacherPrivilege(Classroom classroom, long currentUserId, String errorMessage) {
        if (classroom.getTeacher().getId() != currentUserId) {
            throw new AccessDeniedException(errorMessage);
        }
    }

    private void validateTeacherOrStudentPrivilege(Classroom classroom, long currentUserId) {
        boolean isTeacher = classroom.getTeacher().getId() == currentUserId;
        boolean isStudent = classroom.getStudents().stream().anyMatch(s -> s.getId() == currentUserId);

        if (!isTeacher && !isStudent) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin lớp học này");
        }
    }

    private String generateUniqueClassCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
