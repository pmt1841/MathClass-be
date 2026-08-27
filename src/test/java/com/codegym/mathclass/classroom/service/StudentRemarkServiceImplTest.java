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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRemarkServiceImplTest {

    @Mock
    private StudentRemarkRepository studentRemarkRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StudentRemarkServiceImpl studentRemarkService;

    private Classroom classroom;
    private User teacher;
    private User student;
    private User otherStudent;

    @BeforeEach
    void setUp() {
        teacher = User.builder()
                .fullName("Thầy Nguyễn Văn A")
                .email("teacher@mathclass.com")
                .build();
        teacher.setId(1L);

        student = User.builder()
                .fullName("Học sinh Lê Thị Bình")
                .email("student@mathclass.com")
                .build();
        student.setId(2L);

        otherStudent = User.builder()
                .fullName("Học sinh Trần Văn C")
                .email("other@mathclass.com")
                .build();
        otherStudent.setId(3L);

        Set<User> students = new HashSet<>();
        students.add(student);

        classroom = Classroom.builder()
                .className("Lớp Toán 10A")
                .classCode("MATH101")
                .teacher(teacher)
                .students(students)
                .build();
        classroom.setId(10L);
    }

    @Nested
    @DisplayName("getStudentRemarks Tests")
    class GetStudentRemarksTests {

        @Test
        @DisplayName("Should return list of remarks when requester is the teacher of the classroom")
        void getStudentRemarks_AsTeacher_ReturnsList() {
            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));

            StudentRemark remark = StudentRemark.builder()
                    .classroom(classroom)
                    .student(student)
                    .teacher(teacher)
                    .strengths("Tư duy tốt")
                    .weaknesses("Tính ẩu")
                    .generalAssessment("Khá")
                    .build();
            remark.setId(100L);

            when(studentRemarkRepository.findByClassCodeAndStudentIdOrderByCreatedAtDesc("MATH101", 2L))
                    .thenReturn(List.of(remark));

            List<StudentRemarkResponse> responses = studentRemarkService.getStudentRemarks("MATH101", 2L, 1L);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getStrengths()).isEqualTo("Tư duy tốt");
            verify(studentRemarkRepository).findByClassCodeAndStudentIdOrderByCreatedAtDesc("MATH101", 2L);
        }

        @Test
        @DisplayName("Should return list of remarks when requester is the student himself")
        void getStudentRemarks_AsStudentHimself_ReturnsList() {
            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(studentRemarkRepository.findByClassCodeAndStudentIdOrderByCreatedAtDesc("MATH101", 2L))
                    .thenReturn(Collections.emptyList());

            List<StudentRemarkResponse> responses = studentRemarkService.getStudentRemarks("MATH101", 2L, 2L);

            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when requester is another student")
        void getStudentRemarks_AsOtherStudent_ThrowsAccessDeniedException() {
            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> studentRemarkService.getStudentRemarks("MATH101", 2L, 3L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền xem nhận xét");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when classroom does not exist")
        void getStudentRemarks_ClassroomNotFound_ThrowsResourceNotFoundException() {
            when(classroomRepository.findByClassCode("INVALID")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> studentRemarkService.getStudentRemarks("INVALID", 2L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy lớp học");
        }
    }

    @Nested
    @DisplayName("createStudentRemark Tests")
    class CreateStudentRemarkTests {

        @Test
        @DisplayName("Should create student remark successfully when teacher provides valid data")
        void createStudentRemark_ValidData_ReturnsResponse() {
            CreateStudentRemarkRequest request = new CreateStudentRemarkRequest();
            request.setStrengths("Học bài đầy đủ");
            request.setWeaknesses("Chưa tự giác làm bài tập nâng cao");
            request.setGeneralAssessment("Cần cố gắng thêm");

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(userRepository.findById(2L)).thenReturn(Optional.of(student));

            StudentRemark savedRemark = StudentRemark.builder()
                    .classroom(classroom)
                    .student(student)
                    .teacher(teacher)
                    .strengths(request.getStrengths())
                    .weaknesses(request.getWeaknesses())
                    .generalAssessment(request.getGeneralAssessment())
                    .build();
            savedRemark.setId(200L);

            when(studentRemarkRepository.save(any(StudentRemark.class))).thenReturn(savedRemark);

            StudentRemarkResponse response = studentRemarkService.createStudentRemark("MATH101", 2L, 1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(200L);
            assertThat(response.getStrengths()).isEqualTo("Học bài đầy đủ");
            verify(studentRemarkRepository).save(any(StudentRemark.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when requester is not the classroom teacher")
        void createStudentRemark_NotClassTeacher_ThrowsAccessDeniedException() {
            CreateStudentRemarkRequest request = new CreateStudentRemarkRequest();
            request.setStrengths("Tốt");

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> studentRemarkService.createStudentRemark("MATH101", 2L, 99L, request))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Chỉ giáo viên phụ trách lớp mới có quyền");
        }

        @Test
        @DisplayName("Should throw BadRequestException when student is not a member of the class")
        void createStudentRemark_StudentNotMember_ThrowsBadRequestException() {
            CreateStudentRemarkRequest request = new CreateStudentRemarkRequest();
            request.setStrengths("Tốt");

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(userRepository.findById(3L)).thenReturn(Optional.of(otherStudent));

            assertThatThrownBy(() -> studentRemarkService.createStudentRemark("MATH101", 3L, 1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Học sinh không thuộc lớp học này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when all remark fields are empty")
        void createStudentRemark_AllFieldsEmpty_ThrowsBadRequestException() {
            CreateStudentRemarkRequest request = new CreateStudentRemarkRequest();
            request.setStrengths("   ");
            request.setWeaknesses("");
            request.setGeneralAssessment(null);

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(userRepository.findById(2L)).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> studentRemarkService.createStudentRemark("MATH101", 2L, 1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Vui lòng nhập ít nhất");
        }
    }

    @Nested
    @DisplayName("deleteStudentRemark Tests")
    class DeleteStudentRemarkTests {

        @Test
        @DisplayName("Should delete student remark successfully when requester is the teacher")
        void deleteStudentRemark_AsTeacher_DeletesSuccessfully() {
            StudentRemark remark = StudentRemark.builder()
                    .classroom(classroom)
                    .student(student)
                    .teacher(teacher)
                    .build();
            remark.setId(300L);

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(studentRemarkRepository.findById(300L)).thenReturn(Optional.of(remark));

            studentRemarkService.deleteStudentRemark("MATH101", 2L, 300L, 1L);

            verify(studentRemarkRepository).delete(remark);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when remark does not exist")
        void deleteStudentRemark_RemarkNotFound_ThrowsResourceNotFoundException() {
            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(studentRemarkRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> studentRemarkService.deleteStudentRemark("MATH101", 2L, 999L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy nhận xét");
        }

        @Test
        @DisplayName("Should throw BadRequestException when remark belongs to different classroom or student")
        void deleteStudentRemark_MismatchClassroomOrStudent_ThrowsBadRequestException() {
            Classroom anotherClassroom = Classroom.builder()
                    .classCode("MATH102")
                    .teacher(teacher)
                    .build();
            anotherClassroom.setId(20L);

            StudentRemark remark = StudentRemark.builder()
                    .classroom(anotherClassroom)
                    .student(student)
                    .teacher(teacher)
                    .build();
            remark.setId(300L);

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(studentRemarkRepository.findById(300L)).thenReturn(Optional.of(remark));

            assertThatThrownBy(() -> studentRemarkService.deleteStudentRemark("MATH101", 2L, 300L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nhận xét không thuộc về học sinh hoặc lớp học này");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-owner/non-teacher attempts to delete")
        void deleteStudentRemark_UnauthorizedUser_ThrowsAccessDeniedException() {
            User otherTeacher = User.builder().fullName("Thầy B").build();
            otherTeacher.setId(5L);

            StudentRemark remark = StudentRemark.builder()
                    .classroom(classroom)
                    .student(student)
                    .teacher(otherTeacher)
                    .build();
            remark.setId(300L);

            when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
            when(studentRemarkRepository.findById(300L)).thenReturn(Optional.of(remark));

            assertThatThrownBy(() -> studentRemarkService.deleteStudentRemark("MATH101", 2L, 300L, 99L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền xóa nhận xét này");
        }
    }
}
