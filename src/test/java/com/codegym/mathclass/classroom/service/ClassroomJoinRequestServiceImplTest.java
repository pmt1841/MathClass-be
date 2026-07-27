package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.JoinRequestRequest;
import com.codegym.mathclass.classroom.dto.JoinRequestResponse;
import com.codegym.mathclass.classroom.dto.ProcessJoinRequestDto;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.entity.ClassroomJoinRequest;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomJoinRequestServiceImplTest {

    @Mock
    private ClassroomJoinRequestRepository joinRequestRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ClassroomJoinRequestServiceImpl joinRequestService;

    private User teacher;
    private User student;
    private Classroom classroom;
    private ClassroomJoinRequest pendingRequest;

    private final long teacherId = 1L;
    private final long studentId = 2L;
    private final long requestId = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(joinRequestService, "frontendUrl", "http://localhost:3000");

        teacher = new User();
        teacher.setId(teacherId);
        teacher.setEmail("teacher@codegym.com");
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setRole(Role.TEACHER);

        student = new User();
        student.setId(studentId);
        student.setEmail("student@codegym.com");
        student.setFullName("Tran Thi Student");
        student.setRole(Role.STUDENT);

        classroom = new Classroom();
        classroom.setId(10L);
        classroom.setClassCode("ABC12345");
        classroom.setClassName("Math 101");
        classroom.setTeacher(teacher);
        classroom.setMaxStudents(30);
        classroom.setStudents(new HashSet<>());

        pendingRequest = ClassroomJoinRequest.builder()
                .classroom(classroom)
                .student(student)
                .status(JoinRequestStatus.PENDING)
                .build();
        pendingRequest.setId(requestId);
    }

    @Nested
    @DisplayName("createJoinRequest Tests")
    class CreateJoinRequestTests {

        @Test
        @DisplayName("Should create join request successfully when student and class are valid")
        void createJoinRequest_ValidRequest_Success() {
            JoinRequestRequest requestDto = new JoinRequestRequest();
            requestDto.setClassCode("ABC12345");

            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(joinRequestRepository.findByClassroomIdAndStudentIdAndStatus(10L, studentId, JoinRequestStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(joinRequestRepository.save(any(ClassroomJoinRequest.class))).thenAnswer(i -> i.getArgument(0));

            JoinRequestResponse response = joinRequestService.createJoinRequest(requestDto, studentId);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.PENDING);
            verify(joinRequestRepository, times(1)).save(any(ClassroomJoinRequest.class));
            verify(emailService, times(1)).sendHtmlMailAsync(eq("teacher@codegym.com"), anyString(), anyString(), any());
            verify(notificationService, times(1)).saveAndSendNotification(eq(teacherId), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when student is not found")
        void createJoinRequest_StudentNotFound_ThrowsException() {
            JoinRequestRequest requestDto = new JoinRequestRequest();
            requestDto.setClassCode("ABC12345");

            when(userRepository.findById(studentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> joinRequestService.createJoinRequest(requestDto, studentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy người dùng");

            verify(joinRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when user is not a student")
        void createJoinRequest_UserNotStudent_ThrowsException() {
            JoinRequestRequest requestDto = new JoinRequestRequest();
            requestDto.setClassCode("ABC12345");

            when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

            assertThatThrownBy(() -> joinRequestService.createJoinRequest(requestDto, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ học sinh mới có thể xin vào lớp");

            verify(joinRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when student is already in the classroom")
        void createJoinRequest_AlreadyInClass_ThrowsException() {
            classroom.getStudents().add(student);
            JoinRequestRequest requestDto = new JoinRequestRequest();
            requestDto.setClassCode("ABC12345");

            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> joinRequestService.createJoinRequest(requestDto, studentId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bạn đã ở trong lớp học này rồi");

            verify(joinRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when existing PENDING request exists")
        void createJoinRequest_PendingRequestExists_ThrowsException() {
            JoinRequestRequest requestDto = new JoinRequestRequest();
            requestDto.setClassCode("ABC12345");

            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(joinRequestRepository.findByClassroomIdAndStudentIdAndStatus(10L, studentId, JoinRequestStatus.PENDING))
                    .thenReturn(Optional.of(pendingRequest));

            assertThatThrownBy(() -> joinRequestService.createJoinRequest(requestDto, studentId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bạn đã gửi yêu cầu tham gia lớp này và đang chờ duyệt");

            verify(joinRequestRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getPendingJoinRequests Tests")
    class GetPendingJoinRequestsTests {

        @Test
        @DisplayName("Should return pending join requests for classroom teacher")
        void getPendingJoinRequests_Teacher_ReturnsList() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));
            when(joinRequestRepository.findByClassroomClassCodeAndStatus("ABC12345", JoinRequestStatus.PENDING))
                    .thenReturn(List.of(pendingRequest));

            List<JoinRequestResponse> responses = joinRequestService.getPendingJoinRequests("ABC12345", teacherId);

            assertThat(responses).isNotNull().hasSize(1);
            assertThat(responses.get(0).getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-teacher tries to get pending requests")
        void getPendingJoinRequests_NotTeacher_ThrowsAccessDeniedException() {
            when(classroomRepository.findByClassCode("ABC12345")).thenReturn(Optional.of(classroom));

            assertThatThrownBy(() -> joinRequestService.getPendingJoinRequests("ABC12345", 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không phải là giáo viên phụ trách lớp học này");
        }
    }

    @Nested
    @DisplayName("processJoinRequest Tests")
    class ProcessJoinRequestTests {

        @Test
        @DisplayName("Should approve join request and add student to classroom")
        void processJoinRequest_Approve_Success() {
            ProcessJoinRequestDto processDto = new ProcessJoinRequestDto();
            processDto.setStatus(JoinRequestStatus.APPROVED);

            when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
            when(joinRequestRepository.save(any())).thenReturn(pendingRequest);

            JoinRequestResponse response = joinRequestService.processJoinRequest(requestId, processDto, teacherId);

            assertThat(response).isNotNull();
            assertThat(pendingRequest.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(classroom.getStudents()).contains(student);
            verify(classroomRepository, times(1)).save(classroom);
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@codegym.com"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should reject join request and send notification")
        void processJoinRequest_Reject_Success() {
            ProcessJoinRequestDto processDto = new ProcessJoinRequestDto();
            processDto.setStatus(JoinRequestStatus.REJECTED);

            when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            JoinRequestResponse response = joinRequestService.processJoinRequest(requestId, processDto, teacherId);

            assertThat(response).isNotNull();
            assertThat(pendingRequest.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(classroom.getStudents()).doesNotContain(student);
            verify(classroomRepository, never()).save(classroom);
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@codegym.com"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-owner teacher tries to process request")
        void processJoinRequest_NotOwner_ThrowsAccessDeniedException() {
            ProcessJoinRequestDto processDto = new ProcessJoinRequestDto();
            processDto.setStatus(JoinRequestStatus.APPROVED);

            when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            assertThatThrownBy(() -> joinRequestService.processJoinRequest(requestId, processDto, 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không phải là giáo viên phụ trách lớp học này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when processing an already processed request")
        void processJoinRequest_AlreadyProcessed_ThrowsBadRequestException() {
            pendingRequest.setStatus(JoinRequestStatus.APPROVED);
            ProcessJoinRequestDto processDto = new ProcessJoinRequestDto();
            processDto.setStatus(JoinRequestStatus.REJECTED);

            when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            assertThatThrownBy(() -> joinRequestService.processJoinRequest(requestId, processDto, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Yêu cầu này đã được xử lý");
        }

        @Test
        @DisplayName("Should throw BadRequestException when updating status back to PENDING")
        void processJoinRequest_UpdateToPending_ThrowsBadRequestException() {
            ProcessJoinRequestDto processDto = new ProcessJoinRequestDto();
            processDto.setStatus(JoinRequestStatus.PENDING);

            when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            assertThatThrownBy(() -> joinRequestService.processJoinRequest(requestId, processDto, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không thể cập nhật trạng thái thành PENDING");
        }

        @Test
        @DisplayName("Should throw BadRequestException when approving request but classroom is full")
        void processJoinRequest_ClassroomFull_ThrowsBadRequestException() {
            classroom.setMaxStudents(1);
            User otherStudent = new User();
            otherStudent.setId(99L);
            classroom.getStudents().add(otherStudent);

            ProcessJoinRequestDto processDto = new ProcessJoinRequestDto();
            processDto.setStatus(JoinRequestStatus.APPROVED);

            when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            assertThatThrownBy(() -> joinRequestService.processJoinRequest(requestId, processDto, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Lớp học đã đạt số lượng tối đa");
        }
    }

    @Nested
    @DisplayName("getMyJoinRequests Tests")
    class GetMyJoinRequestsTests {

        @Test
        @DisplayName("Should return list of join requests sent by student")
        void getMyJoinRequests_Student_ReturnsList() {
            when(joinRequestRepository.findByStudentId(studentId)).thenReturn(List.of(pendingRequest));

            List<JoinRequestResponse> responses = joinRequestService.getMyJoinRequests(studentId);

            assertThat(responses).isNotNull().hasSize(1);
            assertThat(responses.get(0).getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        }
    }
}
