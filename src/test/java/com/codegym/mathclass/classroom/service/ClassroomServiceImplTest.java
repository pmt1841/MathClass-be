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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Arrays;
import java.util.HashSet;
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
    private Long currentUserId;

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
        Long studentId = 2L;
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
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        List<StudentResponse> students = classroomService.getStudentsByClassCode("ABC12345", currentUserId);

        assertNotNull(students);
        assertEquals(1, students.size());
        assertEquals(student.getId(), students.get(0).getId());
    }

    @Test
    @DisplayName("should get students by class code successfully for student in the class")
    void getStudentsByClassCode_Success_Student() {
        classroom.getStudents().add(student);
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        List<StudentResponse> students = classroomService.getStudentsByClassCode("ABC12345", student.getId());

        assertNotNull(students);
        assertEquals(1, students.size());
        assertEquals(student.getId(), students.get(0).getId());
    }

    @Test
    @DisplayName("should throw Exception when getting students and user not authorized")
    void getStudentsByClassCode_NotAuthorized_ThrowsException() {
        when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> classroomService.getStudentsByClassCode("ABC12345", 999L));

        assertEquals("Bạn không có quyền xem danh sách học sinh lớp học này", exception.getMessage());
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
}
