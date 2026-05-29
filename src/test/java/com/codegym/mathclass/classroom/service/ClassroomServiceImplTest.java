package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClassroomServiceImpl classroomService;

    private User teacher;
    private CreateClassroomRequest request;
    private String currentUserEmail;

    @BeforeEach
    void setUp() {
        currentUserEmail = "teacher@codegym.com";

        teacher = new User();
        teacher.setId(1L);
        teacher.setEmail(currentUserEmail);
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setRole(Role.TEACHER);
        teacher.setActive(true);

        request = new CreateClassroomRequest();
        request.setName("Math 101");
        request.setMaxStudents(30);
        request.setDescription("Basic mathematics course");
    }

    @Test
    @DisplayName("should create classroom successfully when user is teacher and code is unique")
    void createClassroom_Success() {
        // Arrange
        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.of(teacher));
        when(classroomRepository.existsByClassCode(anyString())).thenReturn(false);

        Classroom savedClassroom = new Classroom();
        savedClassroom.setId(10L);
        savedClassroom.setClassName(request.getName());
        savedClassroom.setMaxStudents(request.getMaxStudents());
        savedClassroom.setDescription(request.getDescription());
        savedClassroom.setTeacher(teacher);

        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> {
            Classroom classroomToSave = invocation.getArgument(0);
            savedClassroom.setClassCode(classroomToSave.getClassCode());
            return savedClassroom;
        });

        // Act
        ClassroomResponse response = classroomService.createClassroom(request, currentUserEmail);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(request.getName(), response.getClassName());
        assertEquals(teacher.getId(), response.getTeacherId());
        assertEquals(teacher.getFullName(), response.getTeacherName());
        assertNotNull(response.getClassCode());
        assertEquals(8, response.getClassCode().length());

        verify(userRepository, times(1)).findByEmail(currentUserEmail);
        verify(classroomRepository, atLeastOnce()).existsByClassCode(anyString());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should throw Exception when user is not found")
    void createClassroom_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                classroomService.createClassroom(request, currentUserEmail)
        );

        assertEquals("Không tìm thấy người dùng", exception.getMessage());
        verify(userRepository, times(1)).findByEmail(currentUserEmail);
        verify(classroomRepository, never()).existsByClassCode(anyString());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should throw Exception when user is not a teacher")
    void createClassroom_UserNotTeacher_ThrowsException() {
        // Arrange
        User student = new User();
        student.setId(2L);
        student.setEmail(currentUserEmail);
        student.setRole(Role.STUDENT);

        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.of(student));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                classroomService.createClassroom(request, currentUserEmail)
        );

        assertEquals("Chỉ giáo viên mới có quyền tạo lớp học", exception.getMessage());
        verify(userRepository, times(1)).findByEmail(currentUserEmail);
        verify(classroomRepository, never()).existsByClassCode(anyString());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should retry generating code when there is a collision")
    void createClassroom_CodeCollision_RetriesAndSucceeds() {
        // Arrange
        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.of(teacher));

        // Mock existsByClassCode: returns true the first time, false the second time
        when(classroomRepository.existsByClassCode(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        Classroom savedClassroom = new Classroom();
        savedClassroom.setId(10L);
        savedClassroom.setClassName(request.getName());
        savedClassroom.setMaxStudents(request.getMaxStudents());
        savedClassroom.setDescription(request.getDescription());
        savedClassroom.setTeacher(teacher);

        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> {
            Classroom classroomToSave = invocation.getArgument(0);
            savedClassroom.setClassCode(classroomToSave.getClassCode());
            return savedClassroom;
        });

        // Act
        ClassroomResponse response = classroomService.createClassroom(request, currentUserEmail);

        // Assert
        assertNotNull(response);
        verify(classroomRepository, times(2)).existsByClassCode(anyString());
        verify(classroomRepository, times(1)).save(any(Classroom.class));
    }

    @Test
    @DisplayName("should return classroom list successfully when user is a teacher")
    void getClassroomsByTeacher_Success() {
        // Arrange
        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.of(teacher));

        Classroom class1 = new Classroom();
        class1.setId(101L);
        class1.setClassName("Math A");
        class1.setClassCode("CODE1234");
        class1.setTeacher(teacher);

        Classroom class2 = new Classroom();
        class2.setId(102L);
        class2.setClassName("Math B");
        class2.setClassCode("CODE5678");
        class2.setTeacher(teacher);

        when(classroomRepository.findByTeacherEmail(currentUserEmail)).thenReturn(Arrays.asList(class1, class2));

        // Act
        List<ClassroomResponse> responses = classroomService.getClassroomsByTeacher(currentUserEmail);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(101L, responses.get(0).getId());
        assertEquals("Math A", responses.get(0).getClassName());
        assertEquals("CODE1234", responses.get(0).getClassCode());
        assertEquals(teacher.getId(), responses.get(0).getTeacherId());

        assertEquals(102L, responses.get(1).getId());
        assertEquals("Math B", responses.get(1).getClassName());
        assertEquals("CODE5678", responses.get(1).getClassCode());
        assertEquals(teacher.getId(), responses.get(1).getTeacherId());

        verify(userRepository, times(1)).findByEmail(currentUserEmail);
        verify(classroomRepository, times(1)).findByTeacherEmail(currentUserEmail);
    }

    @Test
    @DisplayName("should throw Exception when getting classrooms and user is not found")
    void getClassroomsByTeacher_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                classroomService.getClassroomsByTeacher(currentUserEmail)
        );

        assertEquals("Không tìm thấy người dùng", exception.getMessage());
        verify(userRepository, times(1)).findByEmail(currentUserEmail);
        verify(classroomRepository, never()).findByTeacherEmail(anyString());
    }

    @Test
    @DisplayName("should throw Exception when getting classrooms and user is not a teacher")
    void getClassroomsByTeacher_UserNotTeacher_ThrowsException() {
        // Arrange
        User student = new User();
        student.setId(2L);
        student.setEmail(currentUserEmail);
        student.setRole(Role.STUDENT);

        when(userRepository.findByEmail(currentUserEmail)).thenReturn(Optional.of(student));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                classroomService.getClassroomsByTeacher(currentUserEmail)
        );

        assertEquals("Chỉ giáo viên mới có quyền xem danh sách lớp học", exception.getMessage());
        verify(userRepository, times(1)).findByEmail(currentUserEmail);
        verify(classroomRepository, never()).findByTeacherEmail(anyString());
    }
}
