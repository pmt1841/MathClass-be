package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ClassroomServiceImpl classroomService;

    private User teacher;
    private User student;
    private Classroom classroom;
    private CreateClassroomRequest request;
    private long currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = 1L;

        teacher = new User();
        teacher.setId(currentUserId);
        teacher.setEmail("teacher@codegym.com");
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setRole(Role.TEACHER);
        teacher.setActive(true);

        student = new User();
        student.setId(2L);
        student.setEmail("student@codegym.com");
        student.setFullName("Tran Thi Student");
        student.setRole(Role.STUDENT);
        student.setActive(true);

        classroom = new Classroom();
        classroom.setId(10L);
        classroom.setClassCode("ABC12345");
        classroom.setClassName("Math 101");
        classroom.setTeacher(teacher);
        classroom.setMaxStudents(30);
        classroom.setStudents(new HashSet<>());

        request = new CreateClassroomRequest();
        request.setName("Math 101");
        request.setMaxStudents(30);
        request.setDescription("Basic mathematics course");
    }

    // ==========================================
    // Tests for createClassroom
    // ==========================================

    @Test
    @DisplayName("Should create classroom successfully when user is teacher and code is unique")
    void createClassroom_UserIsTeacher_ReturnsClassroomResponse() {
        // Given
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(teacher));
        when(classroomRepository.existsByClassCode(anyString())).thenReturn(false);

        Classroom savedClassroom = new Classroom();
        savedClassroom.setId(10L);
        savedClassroom.setClassName(request.getName());
        savedClassroom.setMaxStudents(request.getMaxStudents());
        savedClassroom.setDescription(request.getDescription());
        savedClassroom.setTeacher(teacher);
        savedClassroom.setStudents(new HashSet<>());

        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> {
            Classroom classroomToSave = invocation.getArgument(0);
            savedClassroom.setClassCode(classroomToSave.getClassCode());
            return savedClassroom;
        });

        // When
        ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getClassName()).isEqualTo(request.getName());
        assertThat(response.getTeacherId()).isEqualTo(teacher.getId());
        assertThat(response.getTeacherName()).isEqualTo(teacher.getFullName());
        assertThat(response.getClassCode()).isNotNull().hasSize(8);

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, atLeastOnce()).existsByClassCode(anyString());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("Should throw Exception when user is not found")
    void createClassroom_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(currentUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.createClassroom(request, currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy người dùng");

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, never()).existsByClassCode(anyString());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    @DisplayName("Should throw Exception when user is not a teacher")
    void createClassroom_UserNotTeacher_ThrowsException() {
        // Given
        User studentUser = new User();
        studentUser.setId(currentUserId);
        studentUser.setRole(Role.STUDENT);

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(studentUser));

        // When & Then
        assertThatThrownBy(() -> classroomService.createClassroom(request, currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Chỉ giáo viên mới có quyền tạo lớp học");

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, never()).existsByClassCode(anyString());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    @DisplayName("Should retry generating code when there is a collision")
    void createClassroom_CodeCollision_RetriesAndSucceeds() {
        // Given
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(teacher));
        when(classroomRepository.existsByClassCode(anyString())).thenReturn(true).thenReturn(false);

        Classroom savedClassroom = new Classroom();
        savedClassroom.setId(10L);
        savedClassroom.setClassName(request.getName());
        savedClassroom.setMaxStudents(request.getMaxStudents());
        savedClassroom.setDescription(request.getDescription());
        savedClassroom.setTeacher(teacher);
        savedClassroom.setStudents(new HashSet<>());

        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> {
            Classroom classroomToSave = invocation.getArgument(0);
            savedClassroom.setClassCode(classroomToSave.getClassCode());
            return savedClassroom;
        });

        // When
        ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

        // Then
        assertThat(response).isNotNull();
        verify(classroomRepository, times(2)).existsByClassCode(anyString());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    // ==========================================
    // Tests for getClassroomsListById
    // ==========================================

    @Test
    @DisplayName("Should return classroom list successfully when user is a teacher")
    void getClassroomsListById_Teacher_ReturnsList() {
        // Given
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(teacher));

        Classroom class1 = new Classroom();
        class1.setId(101L);
        class1.setClassName("Math A");
        class1.setClassCode("CODE1234");
        class1.setTeacher(teacher);
        class1.setStudents(new HashSet<>());

        Classroom class2 = new Classroom();
        class2.setId(102L);
        class2.setClassName("Math B");
        class2.setClassCode("CODE5678");
        class2.setTeacher(teacher);
        class2.setStudents(new HashSet<>());

        when(classroomRepository.findByTeacherId(currentUserId)).thenReturn(Arrays.asList(class1, class2));

        // When
        List<ClassroomResponse> responses = classroomService.getClassroomsListById(currentUserId);

        // Then
        assertThat(responses).isNotNull().hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(101L);
        assertThat(responses.get(1).getId()).isEqualTo(102L);

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, times(1)).findByTeacherId(currentUserId);
    }

    @Test
    @DisplayName("Should return classroom list successfully when user is a student")
    void getClassroomsListById_Student_ReturnsList() {
        // Given
        long studentId = 2L;
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        Classroom class1 = new Classroom();
        class1.setId(101L);
        class1.setClassName("Math A");
        class1.setClassCode("CODE1234");
        class1.setTeacher(teacher);
        class1.setStudents(new HashSet<>(Collections.singletonList(student)));

        when(classroomRepository.findByStudentsId(studentId)).thenReturn(Collections.singletonList(class1));

        // When
        List<ClassroomResponse> responses = classroomService.getClassroomsListById(studentId);

        // Then
        assertThat(responses).isNotNull().hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(101L);

        verify(userRepository, times(1)).findById(studentId);
        verify(classroomRepository, times(1)).findByStudentsId(studentId);
    }

    @Test
    @DisplayName("Should throw Exception when getting classrooms and user is not found")
    void getClassroomsListById_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(currentUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.getClassroomsListById(currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy người dùng");

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, never()).findByTeacherId(anyLong());
    }

    // ==========================================
    // Tests for addStudentToClass
    // ==========================================

    @Test
    @DisplayName("Should add student to classroom successfully and send notification email")
    void addStudentToClass_ValidRequest_AddsStudent() {
        // Given
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));
        when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
        doNothing().when(emailService).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());

        // When
        classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId);

        // Then
        assertThat(classroom.getStudents()).contains(student);
        verify(classroomRepository, times(1)).save(classroom);
        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("student@codegym.com"),
                contains("Math 101"),
                anyString(),
                any());
        verify(notificationService, times(1)).saveAndSendNotification(eq(student.getId()), contains("Math 101"), anyString());
    }

    @Test
    @DisplayName("Should throw Exception when classroom is not found")
    void addStudentToClass_ClassroomNotFound_ThrowsException() {
        // Given
        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.addStudentToClass("NOTEXIST", "student@codegym.com", currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy lớp học");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when requester is not the classroom teacher")
    void addStudentToClass_NotClassroomTeacher_ThrowsException() {
        // Given
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bạn không phải là giáo viên phụ trách lớp học này");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when student email is not found in the system")
    void addStudentToClass_StudentNotFound_ThrowsException() {
        // Given
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("notfound@codegym.com")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "notfound@codegym.com", currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy học sinh với email đã cung cấp");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when the user being added is not a student")
    void addStudentToClass_UserIsNotStudent_ThrowsException() {
        // Given
        User anotherTeacher = new User();
        anotherTeacher.setId(3L);
        anotherTeacher.setEmail("another_teacher@codegym.com");
        anotherTeacher.setRole(Role.TEACHER);

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("another_teacher@codegym.com")).thenReturn(Optional.of(anotherTeacher));

        // When & Then
        assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "another_teacher@codegym.com", currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Người dùng được thêm phải là học sinh");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when student is already in the classroom")
    void addStudentToClass_StudentAlreadyInClass_ThrowsException() {
        // Given
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));

        // When & Then
        assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Học sinh này đã tham gia lớp học");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when classroom is at max capacity")
    void addStudentToClass_ClassroomFull_ThrowsException() {
        // Given
        classroom.setMaxStudents(1);

        User existingStudent = new User();
        existingStudent.setId(99L);
        existingStudent.setEmail("existing@codegym.com");
        existingStudent.setRole(Role.STUDENT);
        classroom.getStudents().add(existingStudent);

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));

        // When & Then
        assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Lớp học đã đạt số lượng tối đa");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    // ==========================================
    // Tests for getStudentsByClassCode
    // ==========================================

    @Test
    @DisplayName("Should get students by class code successfully for teacher")
    void getStudentsByClassCode_Teacher_ReturnsPage() {
        // Given
        classroom.getStudents().add(student);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> studentPage = new PageImpl<>(Collections.singletonList(student));

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findStudentsByClassCode("ABC12345", pageable)).thenReturn(studentPage);

        // When
        Page<StudentResponse> result = classroomService.getStudentsByClassCode("ABC12345", currentUserId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(student.getId());

        verify(classroomRepository, times(1)).findByClassCode("ABC12345");
        verify(userRepository, times(1)).findStudentsByClassCode("ABC12345", pageable);
    }

    @Test
    @DisplayName("Should get students by class code successfully for student in the class")
    void getStudentsByClassCode_Student_ReturnsPage() {
        // Given
        classroom.getStudents().add(student);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> studentPage = new PageImpl<>(Collections.singletonList(student));

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findStudentsByClassCode("ABC12345", pageable)).thenReturn(studentPage);

        // When
        Page<StudentResponse> result = classroomService.getStudentsByClassCode("ABC12345", student.getId(), pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(student.getId());

        verify(classroomRepository, times(1)).findByClassCode("ABC12345");
        verify(userRepository, times(1)).findStudentsByClassCode("ABC12345", pageable);
    }

    @Test
    @DisplayName("Should throw Exception when classroom not found for getStudentsByClassCode")
    void getStudentsByClassCode_ClassroomNotFound_ThrowsException() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.getStudentsByClassCode("NOTEXIST", currentUserId, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy lớp học");

        verify(userRepository, never()).findStudentsByClassCode(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should throw Exception when getting students and user not authorized")
    void getStudentsByClassCode_NotAuthorized_ThrowsException() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.getStudentsByClassCode("ABC12345", 999L, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bạn không có quyền xem thông tin lớp học này");

        verify(userRepository, never()).findStudentsByClassCode(anyString(), any(Pageable.class));
    }

    // ==========================================
    // Tests for getClassroomByClassCode
    // ==========================================

    @Test
    @DisplayName("Should get classroom by class code successfully for teacher")
    void getClassroomByClassCode_Teacher_ReturnsResponse() {
        // Given
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When
        ClassroomResponse response = classroomService.getClassroomByClassCode("ABC12345", currentUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(classroom.getId());
        assertThat(response.getClassCode()).isEqualTo(classroom.getClassCode());
    }

    @Test
    @DisplayName("Should get classroom by class code successfully for student in the class")
    void getClassroomByClassCode_Student_ReturnsResponse() {
        // Given
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When
        ClassroomResponse response = classroomService.getClassroomByClassCode("ABC12345", student.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(classroom.getId());
    }

    @Test
    @DisplayName("Should throw Exception when getting classroom by code and user not authorized")
    void getClassroomByClassCode_NotAuthorized_ThrowsException() {
        // Given
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.getClassroomByClassCode("ABC12345", 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bạn không có quyền xem thông tin lớp học này");
    }

    // ==========================================
    // Tests for removeStudentFromClass
    // ==========================================

    @Test
    @DisplayName("Should remove student from class successfully and send notification email")
    void removeStudentFromClass_ValidRequest_RemovesStudent() {
        // Given
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
        doNothing().when(emailService).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());

        // When
        classroomService.removeStudentFromClass("ABC12345", student.getId(), currentUserId);

        // Then
        assertThat(classroom.getStudents()).doesNotContain(student);
        verify(classroomRepository, times(1)).save(classroom);
        verify(emailService, times(1)).sendHtmlMailAsync(
                eq(student.getEmail()),
                contains("Math 101"),
                anyString(),
                any());
        verify(notificationService, times(1)).saveAndSendNotification(eq(student.getId()), contains("Math 101"), isNull());
    }

    @Test
    @DisplayName("Should throw Exception when classroom not found for removeStudentFromClass")
    void removeStudentFromClass_ClassroomNotFound_ThrowsException() {
        // Given
        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.removeStudentFromClass("NOTEXIST", student.getId(), currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy lớp học");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when requester is not the classroom teacher for removeStudentFromClass")
    void removeStudentFromClass_NotClassroomTeacher_ThrowsException() {
        // Given
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.removeStudentFromClass("ABC12345", student.getId(), 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bạn không phải là giáo viên phụ trách lớp học này");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw Exception when student is not in the classroom for removeStudentFromClass")
    void removeStudentFromClass_StudentNotInClass_ThrowsException() {
        // Given
        // classroom.getStudents() is empty — student not in class
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.removeStudentFromClass("ABC12345", student.getId(), currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Học sinh không tồn tại trong lớp học này");

        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    // ==========================================
    // Tests for updateClassroom
    // ==========================================

    @Test
    @DisplayName("Should update classroom successfully when teacher updates own classroom")
    void updateClassroom_ValidRequest_ReturnsResponse() {
        // Given
        UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                .className("Math 101 Advanced")
                .description("Updated description")
                .maxStudents(50)
                .build();

        Classroom updatedClassroom = new Classroom();
        updatedClassroom.setId(10L);
        updatedClassroom.setClassCode("ABC12345");
        updatedClassroom.setClassName("Math 101 Advanced");
        updatedClassroom.setDescription("Updated description");
        updatedClassroom.setMaxStudents(50);
        updatedClassroom.setTeacher(teacher);
        updatedClassroom.setStudents(new HashSet<>());

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(classroomRepository.save(any(Classroom.class))).thenReturn(updatedClassroom);

        // When
        ClassroomResponse response = classroomService.updateClassroom("ABC12345", updateRequest, currentUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getClassName()).isEqualTo("Math 101 Advanced");
        assertThat(response.getMaxStudents()).isEqualTo(50);
        
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("Should throw Exception when classroom not found for updateClassroom")
    void updateClassroom_ClassroomNotFound_ThrowsException() {
        // Given
        UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                .className("New Name")
                .maxStudents(30)
                .build();

        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> classroomService.updateClassroom("NOTEXIST", updateRequest, currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy lớp học");

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw Exception when requester is not the classroom teacher for updateClassroom")
    void updateClassroom_NotClassroomTeacher_ThrowsException() {
        // Given
        UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                .className("New Name")
                .maxStudents(30)
                .build();

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.updateClassroom("ABC12345", updateRequest, 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bạn không có quyền chỉnh sửa lớp học này");

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw Exception when new maxStudents is less than current student count")
    void updateClassroom_MaxStudentsBelowCurrentCount_ThrowsException() {
        // Given
        User student2 = new User();
        student2.setId(3L);
        student2.setEmail("student2@codegym.com");
        student2.setRole(Role.STUDENT);
        classroom.getStudents().add(student);
        classroom.getStudents().add(student2);

        UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                .className("Math 101")
                .maxStudents(1)  // less than the 2 current students
                .build();

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        // When & Then
        assertThatThrownBy(() -> classroomService.updateClassroom("ABC12345", updateRequest, currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("Sĩ số tối đa không được nhỏ hơn sĩ số học sinh hiện tại");

        verify(classroomRepository, never()).save(any());
    }
}
