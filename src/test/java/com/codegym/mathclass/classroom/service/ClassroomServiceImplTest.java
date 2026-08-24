package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    @Nested
    @DisplayName("createClassroom Tests")
    class CreateClassroomTests {

        @Test
        @DisplayName("Should create classroom successfully when user is teacher and code is unique")
        void createClassroom_UserIsTeacher_ReturnsClassroomResponse() {
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
        @DisplayName("Should throw ResourceNotFoundException when user is not found")
        void createClassroom_UserNotFound_ThrowsException() {
            when(userRepository.findById(currentUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.createClassroom(request, currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy người dùng");

            verify(classroomRepository, never()).save(any(Classroom.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not a teacher")
        void createClassroom_UserNotTeacher_ThrowsException() {
            User studentUser = new User();
            studentUser.setId(currentUserId);
            studentUser.setRole(Role.STUDENT);

            when(userRepository.findById(currentUserId)).thenReturn(Optional.of(studentUser));

            assertThatThrownBy(() -> classroomService.createClassroom(request, currentUserId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Chỉ giáo viên mới có quyền tạo lớp học");

            verify(classroomRepository, never()).save(any(Classroom.class));
        }

        @Test
        @DisplayName("Should retry generating code when there is a collision")
        void createClassroom_CodeCollision_RetriesAndSucceeds() {
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

            ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

            assertThat(response).isNotNull();
            verify(classroomRepository, times(2)).existsByClassCode(anyString());
            verify(classroomRepository, times(1)).save(any(Classroom.class));
        }
    }

    @Nested
    @DisplayName("getClassroomsListById Tests")
    class GetClassroomsListByIdTests {

        @Test
        @DisplayName("Should return classroom list successfully when user is a teacher")
        void getClassroomsListById_Teacher_ReturnsList() {
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

            assertThat(responses).isNotNull().hasSize(2);
            assertThat(responses.get(0).getId()).isEqualTo(101L);
            assertThat(responses.get(1).getId()).isEqualTo(102L);

            verify(userRepository, times(1)).findById(currentUserId);
            verify(classroomRepository, times(1)).findByTeacherId(currentUserId);
        }

        @Test
        @DisplayName("Should return classroom list successfully when user is a student")
        void getClassroomsListById_Student_ReturnsList() {
            long studentId = 2L;
            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

            Classroom class1 = new Classroom();
            class1.setId(101L);
            class1.setClassName("Math A");
            class1.setClassCode("CODE1234");
            class1.setTeacher(teacher);
            class1.setStudents(new HashSet<>(Collections.singletonList(student)));

            when(classroomRepository.findByStudentsId(studentId)).thenReturn(Collections.singletonList(class1));

            List<ClassroomResponse> responses = classroomService.getClassroomsListById(studentId);

            assertThat(responses).isNotNull().hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(101L);

            verify(userRepository, times(1)).findById(studentId);
            verify(classroomRepository, times(1)).findByStudentsId(studentId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user is not found")
        void getClassroomsListById_UserNotFound_ThrowsException() {
            when(userRepository.findById(currentUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.getClassroomsListById(currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy người dùng");

            verify(classroomRepository, never()).findByTeacherId(anyLong());
        }
    }

    @Nested
    @DisplayName("addStudentToClass Tests")
    class AddStudentToClassTests {

        @Test
        @DisplayName("Should add student to classroom successfully and send notification email")
        void addStudentToClass_ValidRequest_AddsStudent() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));
            when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
            doNothing().when(emailService).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());

            classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId);

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
        @DisplayName("Should throw ResourceNotFoundException when classroom is not found")
        void addStudentToClass_ClassroomNotFound_ThrowsException() {
            when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.addStudentToClass("NOTEXIST", "student@codegym.com", currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy lớp học");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when requester is not the classroom teacher")
        void addStudentToClass_NotClassroomTeacher_ThrowsException() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không phải là giáo viên phụ trách lớp học này");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when student email is not found")
        void addStudentToClass_StudentNotFound_ThrowsException() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findByEmail("notfound@codegym.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "notfound@codegym.com", currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy học sinh với email đã cung cấp");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when the user being added is not a student")
        void addStudentToClass_UserIsNotStudent_ThrowsException() {
            User anotherTeacher = new User();
            anotherTeacher.setId(3L);
            anotherTeacher.setEmail("another_teacher@codegym.com");
            anotherTeacher.setRole(Role.TEACHER);

            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findByEmail("another_teacher@codegym.com")).thenReturn(Optional.of(anotherTeacher));

            assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "another_teacher@codegym.com", currentUserId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Người dùng được thêm phải là học sinh");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when student is already in the classroom")
        void addStudentToClass_StudentAlreadyInClass_ThrowsException() {
            classroom.getStudents().add(student);
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Học sinh này đã tham gia lớp học");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when classroom is at max capacity")
        void addStudentToClass_ClassroomFull_ThrowsException() {
            classroom.setMaxStudents(1);
            User existingStudent = new User();
            existingStudent.setId(99L);
            existingStudent.setEmail("existing@codegym.com");
            existingStudent.setRole(Role.STUDENT);
            classroom.getStudents().add(existingStudent);

            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findByEmail("student@codegym.com")).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> classroomService.addStudentToClass("ABC12345", "student@codegym.com", currentUserId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Lớp học đã đạt số lượng tối đa");

            verify(classroomRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getStudentsByClassCode Tests")
    class GetStudentsByClassCodeTests {

        @Test
        @DisplayName("Should get students by class code successfully for teacher")
        void getStudentsByClassCode_Teacher_ReturnsPage() {
            classroom.getStudents().add(student);
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> studentPage = new PageImpl<>(Collections.singletonList(student));

            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findStudentsByClassCode("ABC12345", null, pageable)).thenReturn(studentPage);

            Page<StudentResponse> result = classroomService.getStudentsByClassCode("ABC12345", currentUserId, null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(student.getId());

            verify(classroomRepository, times(1)).findByClassCode("ABC12345");
            verify(userRepository, times(1)).findStudentsByClassCode("ABC12345", null, pageable);
        }

        @Test
        @DisplayName("Should get students by class code successfully for student in the class")
        void getStudentsByClassCode_Student_ReturnsPage() {
            classroom.getStudents().add(student);
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> studentPage = new PageImpl<>(Collections.singletonList(student));

            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(userRepository.findStudentsByClassCode("ABC12345", null, pageable)).thenReturn(studentPage);

            Page<StudentResponse> result = classroomService.getStudentsByClassCode("ABC12345", student.getId(), null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);

            verify(userRepository, times(1)).findStudentsByClassCode("ABC12345", null, pageable);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when classroom not found")
        void getStudentsByClassCode_ClassroomNotFound_ThrowsException() {
            Pageable pageable = PageRequest.of(0, 10);
            when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.getStudentsByClassCode("NOTEXIST", currentUserId, null, pageable))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy lớp học");

            verify(userRepository, never()).findStudentsByClassCode(anyString(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user not authorized to view students")
        void getStudentsByClassCode_NotAuthorized_ThrowsException() {
            Pageable pageable = PageRequest.of(0, 10);
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.getStudentsByClassCode("ABC12345", 999L, null, pageable))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền xem thông tin lớp học này");

            verify(userRepository, never()).findStudentsByClassCode(anyString(), any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("getClassroomByClassCode Tests")
    class GetClassroomByClassCodeTests {

        @Test
        @DisplayName("Should get classroom by class code successfully for teacher")
        void getClassroomByClassCode_Teacher_ReturnsResponse() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            ClassroomResponse response = classroomService.getClassroomByClassCode("ABC12345", currentUserId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(classroom.getId());
            assertThat(response.getClassCode()).isEqualTo(classroom.getClassCode());
        }

        @Test
        @DisplayName("Should get classroom by class code successfully for student in the class")
        void getClassroomByClassCode_Student_ReturnsResponse() {
            classroom.getStudents().add(student);
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            ClassroomResponse response = classroomService.getClassroomByClassCode("ABC12345", student.getId());

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(classroom.getId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when getting classroom by code and user not authorized")
        void getClassroomByClassCode_NotAuthorized_ThrowsException() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.getClassroomByClassCode("ABC12345", 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền xem thông tin lớp học này");
        }
    }

    @Nested
    @DisplayName("removeStudentFromClass Tests")
    class RemoveStudentFromClassTests {

        @Test
        @DisplayName("Should remove student from class successfully and send notification email")
        void removeStudentFromClass_ValidRequest_RemovesStudent() {
            classroom.getStudents().add(student);
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
            doNothing().when(emailService).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());

            classroomService.removeStudentFromClass("ABC12345", student.getId(), currentUserId);

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
        @DisplayName("Should throw ResourceNotFoundException when classroom not found")
        void removeStudentFromClass_ClassroomNotFound_ThrowsException() {
            when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.removeStudentFromClass("NOTEXIST", student.getId(), currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy lớp học");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when requester is not the classroom teacher")
        void removeStudentFromClass_NotClassroomTeacher_ThrowsException() {
            classroom.getStudents().add(student);
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.removeStudentFromClass("ABC12345", student.getId(), 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không phải là giáo viên phụ trách lớp học này");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when student is not in the classroom")
        void removeStudentFromClass_StudentNotInClass_ThrowsException() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.removeStudentFromClass("ABC12345", student.getId(), currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Học sinh không tồn tại trong lớp học này");

            verify(classroomRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateClassroom Tests")
    class UpdateClassroomTests {

        @Test
        @DisplayName("Should update classroom successfully when teacher updates own classroom")
        void updateClassroom_ValidRequest_ReturnsResponse() {
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

            assertThat(response).isNotNull();
            assertThat(response.getClassName()).isEqualTo("Math 101 Advanced");
            assertThat(response.getMaxStudents()).isEqualTo(50);

            verify(classroomRepository, times(1)).save(any(Classroom.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when classroom not found")
        void updateClassroom_ClassroomNotFound_ThrowsException() {
            UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                    .className("New Name")
                    .maxStudents(30)
                    .build();

            when(classroomRepository.findByClassCode("NOTEXIST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classroomService.updateClassroom("NOTEXIST", updateRequest, currentUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy lớp học");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when requester is not the classroom teacher")
        void updateClassroom_NotClassroomTeacher_ThrowsException() {
            UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                    .className("New Name")
                    .maxStudents(30)
                    .build();

            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.updateClassroom("ABC12345", updateRequest, 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền chỉnh sửa lớp học này");

            verify(classroomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when new maxStudents is less than current student count")
        void updateClassroom_MaxStudentsBelowCurrentCount_ThrowsException() {
            User student2 = new User();
            student2.setId(3L);
            student2.setEmail("student2@codegym.com");
            student2.setRole(Role.STUDENT);
            classroom.getStudents().add(student);
            classroom.getStudents().add(student2);

            UpdateClassroomRequest updateRequest = UpdateClassroomRequest.builder()
                    .className("Math 101")
                    .maxStudents(1)
                    .build();

            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.updateClassroom("ABC12345", updateRequest, currentUserId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageStartingWith("Sĩ số tối đa không được nhỏ hơn sĩ số học sinh hiện tại");

            verify(classroomRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteClassroom Tests")
    class DeleteClassroomTests {

        @Test
        @DisplayName("Should delete classroom successfully when classroom has no students")
        void deleteClassroom_NoStudents_Success() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            classroomService.deleteClassroom("ABC12345", currentUserId);

            verify(classroomRepository, times(1)).delete(classroom);
        }

        @Test
        @DisplayName("Should throw BadRequestException when deleting classroom that has students")
        void deleteClassroom_HasStudents_ThrowsBadRequestException() {
            classroom.getStudents().add(student);
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.deleteClassroom("ABC12345", currentUserId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không thể xóa lớp học đã có học sinh tham gia");

            verify(classroomRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-teacher tries to delete classroom")
        void deleteClassroom_NotOwner_ThrowsAccessDeniedException() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> classroomService.deleteClassroom("ABC12345", 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền xóa lớp học này");

            verify(classroomRepository, never()).delete(any());
        }
    }
}
