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

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    @DisplayName("should create classroom successfully when user is teacher and code is unique")
    void createClassroom_Success() {
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

        ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(request.getName(), response.getClassName());
        assertEquals(teacher.getId(), response.getTeacherId());
        assertEquals(teacher.getFullName(), response.getTeacherName());
        assertNotNull(response.getClassCode());
        assertEquals(8, response.getClassCode().length());

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, atLeastOnce()).existsByClassCode(anyString());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should throw Exception when user is not found")
    void createClassroom_UserNotFound_ThrowsException() {
        when(userRepository.findById(currentUserId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.createClassroom(request, currentUserId));

        assertEquals("Không tìm thấy người dùng", exception.getMessage());
        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, never()).existsByClassCode(anyString());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should throw Exception when user is not a teacher")
    void createClassroom_UserNotTeacher_ThrowsException() {
        User studentUser = new User();
        studentUser.setId(currentUserId);
        studentUser.setRole(Role.STUDENT);

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(studentUser));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.createClassroom(request, currentUserId));

        assertEquals("Chỉ giáo viên mới có quyền tạo lớp học", exception.getMessage());
        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, never()).existsByClassCode(anyString());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should retry generating code when there is a collision")
    void createClassroom_CodeCollision_RetriesAndSucceeds() {
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(teacher));

        when(classroomRepository.existsByClassCode(anyString()))
                .thenReturn(true)
                .thenReturn(false);

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

        ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

        assertNotNull(response);
        verify(classroomRepository, times(2)).existsByClassCode(anyString());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should return classroom list successfully when user is a teacher")
    void getClassroomsListById_Teacher_Success() {
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

        List<ClassroomResponse> responses = classroomService.getClassroomsListById(currentUserId);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(101L, responses.get(0).getId());
        assertEquals(102L, responses.get(1).getId());

        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, times(1)).findByTeacherId(currentUserId);
    }

    @Test
    @DisplayName("should return classroom list successfully when user is a student")
    void getClassroomsListById_Student_Success() {
        long studentId = 2L;
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        Classroom class1 = new Classroom();
        class1.setId(101L);
        class1.setClassName("Math A");
        class1.setClassCode("CODE1234");
        class1.setTeacher(teacher);
        class1.setStudents(new HashSet<>(Arrays.asList(student)));

        when(classroomRepository.findByStudentsId(studentId)).thenReturn(Arrays.asList(class1));

        List<ClassroomResponse> responses = classroomService.getClassroomsListById(studentId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(101L, responses.get(0).getId());

        verify(userRepository, times(1)).findById(studentId);
        verify(classroomRepository, times(1)).findByStudentsId(studentId);
    }

    @Test
    @DisplayName("should throw Exception when getting classrooms and user is not found")
    void getClassroomsListById_UserNotFound_ThrowsException() {
        when(userRepository.findById(currentUserId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.getClassroomsListById(currentUserId));

        assertEquals("Không tìm thấy người dùng", exception.getMessage());
        verify(userRepository, times(1)).findById(currentUserId);
        verify(classroomRepository, never()).findByTeacherId(anyLong());
    }

    @Test
    @DisplayName("should add student to classroom successfully and send notification email")
    void addStudentToClass_Success() {
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));
        when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
        doNothing().when(emailService).sendMail(anyString(), anyString(), anyString());

        classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId);

        assertTrue(classroom.getStudents().contains(student));
        verify(classroomRepository, times(1)).save(classroom);
        verify(emailService, times(1)).sendMail(
                eq("student@codegym.com"),
                contains("Math 101"),
                anyString());
    }

    @Test
    @DisplayName("should throw Exception when classroom is not found")
    void addStudentToClass_ClassroomNotFound_ThrowsException() {
        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.addStudentToClass("NOTEXIST", "student@codegym.com", currentUserId));

        assertEquals("Không tìm thấy lớp học", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when requester is not the classroom teacher")
    void addStudentToClass_NotClassroomTeacher_ThrowsException() {
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> classroomService
                .addStudentToClass("ABC12345", "student@codegym.com", 999L));

        assertEquals("Bạn không phải là giáo viên phụ trách lớp học này", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when student email is not found in the system")
    void addStudentToClass_StudentNotFound_ThrowsException() {
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("notfound@codegym.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.addStudentToClass("ABC12345", "notfound@codegym.com", currentUserId));

        assertEquals("Không tìm thấy học sinh với email đã cung cấp", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when the user being added is not a student")
    void addStudentToClass_UserIsNotStudent_ThrowsException() {
        User anotherTeacher = new User();
        anotherTeacher.setId(3L);
        anotherTeacher.setEmail("another_teacher@codegym.com");
        anotherTeacher.setRole(Role.TEACHER);

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("another_teacher@codegym.com")).thenReturn(Optional.of(anotherTeacher));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.addStudentToClass("ABC12345", "another_teacher@codegym.com", currentUserId));

        assertEquals("Người dùng được thêm phải là học sinh", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when student is already in the classroom")
    void addStudentToClass_StudentAlreadyInClass_ThrowsException() {
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId));

        assertEquals("Học sinh này đã tham gia lớp học", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when classroom is at max capacity")
    void addStudentToClass_ClassroomFull_ThrowsException() {
        classroom.setMaxStudents(1);

        User existingStudent = new User();
        existingStudent.setId(99L);
        existingStudent.setEmail("existing@codegym.com");
        existingStudent.setRole(Role.STUDENT);
        classroom.getStudents().add(existingStudent);

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId));

        assertEquals("Lớp học đã đạt số lượng tối đa", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    // =====================================================
    // Tests for getStudentsByClassCode
    // =====================================================

    @Test
    @DisplayName("should get students by class code successfully for teacher")
    void getStudentsByClassCode_Success_Teacher() {
        classroom.getStudents().add(student);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> studentPage = new PageImpl<>(Collections.singletonList(student));

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findStudentsByClassCode("ABC12345", pageable)).thenReturn(studentPage);

        Page<StudentResponse> result = classroomService.getStudentsByClassCode("ABC12345", currentUserId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(student.getId(), result.getContent().get(0).getId());

        verify(classroomRepository, times(1)).findByClassCode("ABC12345");
        verify(userRepository, times(1)).findStudentsByClassCode("ABC12345", pageable);
    }

    @Test
    @DisplayName("should get students by class code successfully for student in the class")
    void getStudentsByClassCode_Success_Student() {
        classroom.getStudents().add(student);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> studentPage = new PageImpl<>(Collections.singletonList(student));

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(userRepository.findStudentsByClassCode("ABC12345", pageable)).thenReturn(studentPage);

        Page<StudentResponse> result = classroomService.getStudentsByClassCode("ABC12345", student.getId(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(student.getId(), result.getContent().get(0).getId());

        verify(classroomRepository, times(1)).findByClassCode("ABC12345");
        verify(userRepository, times(1)).findStudentsByClassCode("ABC12345", pageable);
    }

    @Test
    @DisplayName("should throw Exception when classroom not found for getStudentsByClassCode")
    void getStudentsByClassCode_ClassroomNotFound_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.getStudentsByClassCode("NOTEXIST", currentUserId, pageable));

        assertEquals("Không tìm thấy lớp học", exception.getMessage());
        verify(userRepository, never()).findStudentsByClassCode(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("should throw Exception when getting students and user not authorized")
    void getStudentsByClassCode_NotAuthorized_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.getStudentsByClassCode("ABC12345", 999L, pageable));

        assertEquals("Bạn không có quyền xem danh sách học sinh lớp học này", exception.getMessage());
        verify(userRepository, never()).findStudentsByClassCode(anyString(), any(Pageable.class));
    }

    // =====================================================
    // Tests for getClassroomByClassCode
    // =====================================================

    @Test
    @DisplayName("should get classroom by class code successfully for teacher")
    void getClassroomByClassCode_Success_Teacher() {
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        ClassroomResponse response = classroomService.getClassroomByClassCode("ABC12345", currentUserId);

        assertNotNull(response);
        assertEquals(classroom.getId(), response.getId());
        assertEquals(classroom.getClassCode(), response.getClassCode());
    }

    @Test
    @DisplayName("should get classroom by class code successfully for student in the class")
    void getClassroomByClassCode_Success_Student() {
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        ClassroomResponse response = classroomService.getClassroomByClassCode("ABC12345", student.getId());

        assertNotNull(response);
        assertEquals(classroom.getId(), response.getId());
    }

    @Test
    @DisplayName("should throw Exception when getting classroom by code and user not authorized")
    void getClassroomByClassCode_NotAuthorized_ThrowsException() {
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.getClassroomByClassCode("ABC12345", 999L));

        assertEquals("Bạn không có quyền xem thông tin lớp học này", exception.getMessage());
    }

    // =====================================================
    // Tests for removeStudentFromClass
    // =====================================================

    @Test
    @DisplayName("should remove student from class successfully and send notification email")
    void removeStudentFromClass_Success() {
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
        when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
        doNothing().when(emailService).sendMail(anyString(), anyString(), anyString());

        classroomService.removeStudentFromClass("ABC12345", student.getId(), currentUserId);

        assertFalse(classroom.getStudents().contains(student));
        verify(classroomRepository, times(1)).save(classroom);
        verify(emailService, times(1)).sendMail(
                eq(student.getEmail()),
                contains("Math 101"),
                anyString());
    }

    @Test
    @DisplayName("should throw Exception when classroom not found for removeStudentFromClass")
    void removeStudentFromClass_ClassroomNotFound_ThrowsException() {
        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.removeStudentFromClass("NOTEXIST", student.getId(), currentUserId));

        assertEquals("Không tìm thấy lớp học", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when requester is not the classroom teacher for removeStudentFromClass")
    void removeStudentFromClass_NotClassroomTeacher_ThrowsException() {
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.removeStudentFromClass("ABC12345", student.getId(), 999L));

        assertEquals("Bạn không phải là giáo viên phụ trách lớp học này", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw Exception when student is not in the classroom for removeStudentFromClass")
    void removeStudentFromClass_StudentNotInClass_ThrowsException() {
        // classroom.getStudents() is empty — student not in class
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.removeStudentFromClass("ABC12345", student.getId(), currentUserId));

        assertEquals("Học sinh không tồn tại trong lớp học này", exception.getMessage());
        verify(classroomRepository, never()).save(any());
        verify(emailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    // =====================================================
    // Tests for updateClassroom
    // =====================================================

    @Test
    @DisplayName("should update classroom successfully when teacher updates own classroom")
    void updateClassroom_Success() {
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

        ClassroomResponse response = classroomService.updateClassroom("ABC12345", updateRequest, currentUserId);

        assertNotNull(response);
        assertEquals("Math 101 Advanced", response.getClassName());
        assertEquals(50, response.getMaxStudents());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should throw Exception when classroom not found for updateClassroom")
    void updateClassroom_ClassroomNotFound_ThrowsException() {
        UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                .className("New Name")
                .maxStudents(30)
                .build();

        when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.updateClassroom("NOTEXIST", updateRequest, currentUserId));

        assertEquals("Không tìm thấy lớp học", exception.getMessage());
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw Exception when requester is not the classroom teacher for updateClassroom")
    void updateClassroom_NotClassroomTeacher_ThrowsException() {
        UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                .className("New Name")
                .maxStudents(30)
                .build();

        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.updateClassroom("ABC12345", updateRequest, 999L));

        assertEquals("Bạn không có quyền chỉnh sửa lớp học này", exception.getMessage());
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw Exception when new maxStudents is less than current student count")
    void updateClassroom_MaxStudentsBelowCurrentCount_ThrowsException() {
        // Add 2 students to the classroom
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

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.updateClassroom("ABC12345", updateRequest, currentUserId));

        assertTrue(exception.getMessage().startsWith("Sĩ số tối đa không được nhỏ hơn sĩ số học sinh hiện tại"));
        verify(classroomRepository, never()).save(any());
    }
}
